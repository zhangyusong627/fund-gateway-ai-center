package org.practice.fundgateway.experiments;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.integration.contract.ContractCandidate;
import org.practice.fundgateway.integration.contract.DeepSeekCandidateExtractionService;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingModel;
import org.practice.fundgateway.knowledge.rag.RagEvidenceQueryService;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** M3 真实联调：把 M2 已接受证据交给 DeepSeek 抽取候选接口规范。 */
@Component
@Profile("m3-real")
public class M3CandidateExtractionExperiment implements CommandLineRunner {

    private static final String MODEL = "deepseek-v4-flash";
    private static final String COLLECTION = "m2_shengheng_credit_application_v1";
    private static final Path MODEL_PATH = resolvePath("models", "bge-small-zh-v1.5");
    private static final Path EVIDENCE_ROOT = resolvePath("docs", "learning");

    /** 启动一次真实候选规范抽取并保存完整证据。 */
    @Override
    public void run(String... args) {
        try {
            executeOnce();
        } catch (Exception exception) {
            throw new IllegalStateException("M3 候选规范真实抽取失败："
                    + exception.getClass().getSimpleName(), exception);
        }
    }

    /** 查询 RAG、调用模型、执行 Java 校验并保存结果。 */
    private void executeOnce() throws Exception {
        String apiKey = requireEnv("DEEPSEEK_API_KEY");
        Path evidence = EVIDENCE_ROOT.resolve("M3-candidate-real-call-" + System.currentTimeMillis());
        Files.createDirectories(evidence);
        String question = "请从证据中抽取授信申请接口的用途、请求地址和申请金额字段规范";
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
        dataSource.setUser("fund_demo");
        dataSource.setPassword("fund_demo_password");
        RagEvidenceQueryService.RagEvidenceResponse response;
        try (LocalBgeEmbeddingModel embedding = new LocalBgeEmbeddingModel(MODEL_PATH)) {
            RagEvidenceQueryService rag = new RagEvidenceQueryService(new JdbcTemplate(dataSource));
            RagEvidenceQueryService.RagEvidenceResponse interfaceEvidence = rag.query(
                    COLLECTION, "授信申请接口说明和请求地址是什么？", embedding.embed("授信申请接口说明和请求地址是什么？"),
                    Set.of("授信申请", "请求地址"), 3);
            RagEvidenceQueryService.RagEvidenceResponse fieldEvidence = rag.query(
                    COLLECTION, "applyAmt 的类型、必填性和含义是什么？", embedding.embed("applyAmt 的类型、必填性和含义是什么？"),
                    Set.of("applyAmt", "BigDecimal", "必填"), 3);
            RagEvidenceQueryService.RagEvidenceResponse protocolEvidence = rag.query(
                    COLLECTION, "授信申请接口使用什么 HTTP 请求方法？",
                    embedding.embed("授信申请接口使用什么 HTTP 请求方法？"),
                    Set.of("POST", "请求"), 3);
            if (interfaceEvidence.status() != RagEvidenceQueryService.Status.ACCEPTED
                    || fieldEvidence.status() != RagEvidenceQueryService.Status.ACCEPTED
                    || protocolEvidence.status() != RagEvidenceQueryService.Status.ACCEPTED) {
                throw new IllegalStateException("接口或字段证据未通过门禁");
            }
            Map<String, RagEvidenceQueryService.EvidenceCitation> merged = new LinkedHashMap<>();
            interfaceEvidence.evidence().forEach(item -> merged.put(item.chunkId(), item));
            fieldEvidence.evidence().forEach(item -> merged.put(item.chunkId(), item));
            protocolEvidence.evidence().forEach(item -> merged.put(item.chunkId(), item));
            response = RagEvidenceQueryService.RagEvidenceResponse.accepted(question, merged.values().stream().toList());
        }
        JsonMapper mapper = JsonMapper.builder().build();
        save(evidence.resolve("rag-evidence.json"), mapper.writeValueAsString(response));
        if (response.status() != RagEvidenceQueryService.Status.ACCEPTED) {
            throw new IllegalStateException("RAG 证据未通过门禁：" + response.missingKeywords());
        }
        DeepSeekApi api = DeepSeekApi.builder().baseUrl("https://api.deepseek.com").apiKey(apiKey).build();
        DeepSeekCandidateExtractionService extraction = new DeepSeekCandidateExtractionService(api);
        DeepSeekCandidateExtractionService.ExtractionResult result;
        try {
            result = extraction.extract(response, "synthetic-provider", "credit-application");
        } catch (DeepSeekCandidateExtractionService.ExtractionRejectedException rejected) {
            save(evidence.resolve("request.json"), rejected.rawRequest());
            save(evidence.resolve("response.json"), rejected.rawResponse());
            save(evidence.resolve("status.txt"), "model=" + MODEL + "\nstatus=REJECTED\n"
                    + "message=" + rejected.getMessage() + "\n");
            throw rejected;
        }
        save(evidence.resolve("request.json"), result.rawRequest());
        save(evidence.resolve("response.json"), result.rawResponse());
        ContractCandidate candidate = result.candidate();
        save(evidence.resolve("candidate.json"), mapper.writeValueAsString(candidate));
        save(evidence.resolve("status.txt"), "model=" + MODEL + "\nstatus=ACCEPTED\n"
                + "candidateId=" + candidate.candidateId() + "\n");
        System.out.println("M3 候选规范真实抽取完成，证据目录=" + evidence);
    }

    /** 读取密钥但不把密钥值写入日志。 */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    /** 用新文件保存原始报文，避免覆盖历史记录。 */
    private static void save(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /** 兼容从仓库根目录或模块目录启动。 */
    private static Path resolvePath(String first, String second) {
        Path rootRelative = Path.of(first, second);
        return Files.exists(rootRelative) ? rootRelative : Path.of("..", first, second);
    }
}
