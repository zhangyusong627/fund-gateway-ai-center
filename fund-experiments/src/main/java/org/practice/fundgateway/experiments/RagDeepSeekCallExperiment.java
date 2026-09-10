package org.practice.fundgateway.experiments;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingModel;
import org.practice.fundgateway.knowledge.rag.RagEvidenceQueryService;
import org.practice.fundgateway.knowledge.rag.RagPromptBuilder;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** M2 真实 RAG 联调：只把通过证据门禁的引用交给 DeepSeek。 */
@Component
@Profile("m2-real")
public class RagDeepSeekCallExperiment implements CommandLineRunner {

    private static final String MODEL = "deepseek-v4-flash";
    private static final String COLLECTION = "m2_shengheng_credit_application_v1";
    private static final Path MODEL_PATH = resolvePath("models", "bge-small-zh-v1.5");
    private static final Path EVIDENCE_ROOT = resolvePath("docs", "learning");
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 启动一次真实 RAG 调用并保存原始报文和门禁结果。 */
    @Override
    public void run(String... args) {
        try {
            executeOnce();
        } catch (Exception exception) {
            throw new IllegalStateException("M2 RAG 真实调用失败："
                    + exception.getClass().getSimpleName(), exception);
        }
    }

    /** 完成检索、提示词组装、模型调用和最终 JSON/引用校验。 */
    private void executeOnce() throws Exception {
        String apiKey = requireEnv("DEEPSEEK_API_KEY");
        Path evidence = EVIDENCE_ROOT.resolve("M2-rag-real-call-" + System.currentTimeMillis());
        Files.createDirectories(evidence);
        String question = "applyAmt 的类型和必填性是什么";
        Set<String> keywords = Set.of("applyAmt", "BigDecimal", "必填");

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
        dataSource.setUser("fund_demo");
        dataSource.setPassword("fund_demo_password");
        RagEvidenceQueryService.RagEvidenceResponse evidenceResponse;
        try (LocalBgeEmbeddingModel embedding = new LocalBgeEmbeddingModel(MODEL_PATH)) {
            RagEvidenceQueryService service = new RagEvidenceQueryService(new JdbcTemplate(dataSource));
            evidenceResponse = service.query(COLLECTION, question, embedding.embed(question), keywords, 3);
        }
        if (evidenceResponse.status() != RagEvidenceQueryService.Status.ACCEPTED) {
            throw new IllegalStateException("RAG 证据未通过门禁：" + evidenceResponse.missingKeywords());
        }

        RagPromptBuilder.RagPrompt prompt = new RagPromptBuilder().build(evidenceResponse);
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(
                        new ChatCompletionMessage(prompt.systemInstruction(), ChatCompletionMessage.Role.SYSTEM),
                        new ChatCompletionMessage(prompt.userInput(), ChatCompletionMessage.Role.USER)))
                .stream(false)
                .maxTokens(512)
                .thinking(ChatCompletionRequest.Thinking.DISABLED)
                .build();
        save(evidence.resolve("request.json"), mapper.writeValueAsString(request));

        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .build();
        var response = api.chatCompletionEntity(request);
        if (response.getBody() == null || response.getBody().choices() == null
                || response.getBody().choices().isEmpty()) {
            throw new IllegalStateException("模型响应没有 choices");
        }
        save(evidence.resolve("response.json"), mapper.writeValueAsString(response.getBody()));
        String content = response.getBody().choices().getFirst().message().content();
        ValidationResult validation = validateResponse(content, evidenceResponse);
        save(evidence.resolve("validation.json"), mapper.writeValueAsString(validation));
        save(evidence.resolve("status.txt"), "model=" + MODEL
                + "\nstatus=" + validation.status()
                + "\naccepted_citations=" + validation.acceptedCitations() + "\n");
        System.out.println("M2 RAG 真实调用完成，证据目录=" + evidence);
    }

    /** 校验 JSON 结构和引用白名单，不让模型伪造来源。 */
    @SuppressWarnings("unchecked")
    private ValidationResult validateResponse(String content,
                                              RagEvidenceQueryService.RagEvidenceResponse evidenceResponse)
            throws Exception {
        if (content == null || content.isBlank()) {
            return new ValidationResult("REJECTED_EMPTY", 0, "模型返回为空");
        }
        Map<String, Object> report;
        try {
            report = mapper.readValue(content, Map.class);
        } catch (Exception exception) {
            return new ValidationResult("REJECTED_INVALID_JSON", 0, "模型返回不是合法 JSON");
        }
        if (!(report.get("answer") instanceof String)
                || !(report.get("citations") instanceof List<?> citations)) {
            return new ValidationResult("REJECTED_SCHEMA", 0, "缺少 answer 或 citations");
        }
        String answer = (String) report.get("answer");
        if (!containsChinese(answer)) {
            return new ValidationResult("REJECTED_LANGUAGE", 0, "answer 不包含中文");
        }
        if (citations.isEmpty()) {
            return new ValidationResult("REJECTED_NO_CITATION", 0, "回答没有引用证据");
        }
        Map<String, RagEvidenceQueryService.EvidenceCitation> allowed = new HashMap<>();
        evidenceResponse.evidence().forEach(citation -> allowed.put(citation.chunkId(), citation));
        int accepted = 0;
        for (Object citation : citations) {
            if (!(citation instanceof Map<?, ?> citationMap)) {
                return new ValidationResult("REJECTED_CITATION_SCHEMA", accepted, "引用项不是对象");
            }
            Object chunkId = citationMap.get("chunkId");
            Object locator = citationMap.get("locator");
            Object quote = citationMap.get("quote");
            String quoteText = quote instanceof String ? (String) quote : null;
            if (!(chunkId instanceof String) || !(locator instanceof String)
                    || quoteText == null || quoteText.isBlank()
                    || !allowed.containsKey(chunkId)
                    || !allowed.get(chunkId).locator().equals(locator)
                    || !citationTextMatches(allowed.get(chunkId).content(), quoteText)) {
                return new ValidationResult("REJECTED_CITATION_NOT_ALLOWED", accepted,
                        "引用不在证据白名单或原文不匹配");
            }
            accepted++;
        }
        return new ValidationResult("ACCEPTED", accepted, "JSON 结构和引用白名单通过");
    }

    /** 检查答案是否包含中文，技术标识和数字仍允许出现在答案中。 */
    private boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    /** 检查引用文本是否能在对应证据原文中定位。 */
    private boolean citationTextMatches(String evidence, String quote) {
        String normalizedEvidence = evidence.replace("当前行：", "").replaceAll("\\s+", "");
        String normalizedQuote = quote.replace("当前行：", "").replaceAll("\\s+", "");
        return normalizedEvidence.contains(normalizedQuote);
    }

    /** 读取密钥但不把密钥值写入异常和证据。 */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    /** 使用新文件写入证据，避免覆盖历史联调记录。 */
    private static void save(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /** 兼容从仓库根目录或 fund-experiments 模块目录启动。 */
    private static Path resolvePath(String first, String second) {
        Path rootRelative = Path.of(first, second);
        return Files.exists(rootRelative) ? rootRelative : Path.of("..", first, second);
    }

    /** 保存最终门禁状态。 */
    private record ValidationResult(String status, int acceptedCitations, String message) {
    }
}
