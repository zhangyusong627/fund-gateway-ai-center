package org.practice.fundgateway.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.practice.fundgateway.knowledge.credit.CreditApplication;
import org.practice.fundgateway.knowledge.credit.CreditApplicationStructuredOutputService;
import org.practice.fundgateway.knowledge.credit.ValidationIssue;
import org.practice.fundgateway.knowledge.credit.ValidationResult;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** D2 单次真实模型联调：生成合成授信候选并交给确定性校验。 */
@Component
@Profile("d2")
public class DeepSeekStructuredOutputExperiment implements CommandLineRunner {

    /** DeepSeek 兼容接口地址。 */
    private static final String BASE_URL = "https://api.deepseek.com";
    /** D1 已冻结的模型标识。 */
    private static final String MODEL = "deepseek-v4-flash";
    /** 单次实验的最大输出长度。 */
    private static final int MAX_OUTPUT_TOKENS = 512;
    /** D2 证据目录。 */
    private static final String EVIDENCE_ROOT = "docs/learning";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final CreditApplicationStructuredOutputService validationService =
            new CreditApplicationStructuredOutputService();

    @Override
    public void run(String... args) {
        try {
            executeOnce();
        } catch (Exception exception) {
            throw new IllegalStateException("D2 failed: " + exception.getClass().getSimpleName());
        }
    }

    /** 执行一次模型调用、映射和两层确定性校验。 */
    private void executeOnce() throws IOException {
        String apiKey = requireEnv("DEEPSEEK_API_KEY");
        String prompt = requireEnv("D2_PROMPT");
        Path evidenceDir = createEvidenceDirectory();
        DeepSeekApi api = DeepSeekApi.builder().baseUrl(BASE_URL).apiKey(apiKey).build();
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(new ChatCompletionMessage(prompt, ChatCompletionMessage.Role.USER)))
                .stream(false)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .thinking(ChatCompletionRequest.Thinking.DISABLED)
                .build();
        long started = System.nanoTime();
        var response = api.chatCompletionEntity(request);
        if (response.getBody() == null || response.getBody().choices() == null
                || response.getBody().choices().isEmpty()) {
            throw new IOException("Missing model choices");
        }
        String modelText = response.getBody().choices().getFirst().message().content();
        if (modelText == null || modelText.isBlank() || modelText.contains(apiKey) || modelText.contains(prompt)) {
            throw new IOException("Unsafe or empty model response");
        }
        CreditApplicationStructuredOutputService.MappingResult mapping = validationService.map(modelText);
        ValidationResult structure = mapping.successful()
                ? validationService.validateStructure(mapping.application())
                : ValidationResult.failed(List.of(mapping.issue()));
        ValidationResult business = mapping.successful() && structure.valid()
                ? validationService.validateBusiness(mapping.application())
                : ValidationResult.failed(structure.issues());
        business = enforceSyntheticDataBoundary(mapping.application(), business);
        saveEvidence(evidenceDir.resolve("result.txt"), formatResult(response, prompt, structure, business, started));
        saveEvidence(evidenceDir.resolve("response.json"), modelText);
        System.out.println("D2 structured call completed; evidence=" + evidenceDir);
    }

    /** 拒绝模型生成的疑似真实身份数据，确保实验只处理合成候选。 */
    private ValidationResult enforceSyntheticDataBoundary(CreditApplication application,
                                                            ValidationResult current) {
        if (!current.valid() || application == null) {
            return current;
        }
        boolean synthetic = application.billNo() != null && application.billNo().startsWith("SYN-")
                && application.idNo() != null && application.idNo().startsWith("SYNTHETIC-")
                && application.custName() != null && application.custName().contains("合成");
        if (synthetic) {
            return current;
        }
        return ValidationResult.failed(List.of(
                new ValidationIssue("$", "模型输出未满足合成数据约束，拒绝进入后续流程")));
    }

    /** 保存不包含提示词和密钥的实验元数据。 */
    private String formatResult(Object response, String prompt, ValidationResult structure,
                                ValidationResult business, long started) throws IOException {
        return "model=" + MODEL
                + "\nprompt_sha256=" + sha256(prompt)
                + "\nelapsed_ms=" + (System.nanoTime() - started) / 1_000_000
                + "\nstructure_valid=" + structure.valid()
                + "\nstructure_issues=" + jsonMapper.writeValueAsString(structure.issues())
                + "\nbusiness_valid=" + business.valid()
                + "\nbusiness_issues=" + jsonMapper.writeValueAsString(business.issues())
                + "\nresponse_type=" + response.getClass().getSimpleName() + "\n";
    }

    /** 创建不会覆盖历史证据的目录。 */
    private static Path createEvidenceDirectory() throws IOException {
        Path directory = Path.of(EVIDENCE_ROOT, "D2-real-call-" + System.currentTimeMillis());
        Files.createDirectory(directory);
        return directory;
    }

    /** 读取必需环境变量，不把变量值写入错误信息。 */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        return value;
    }

    /** 对提示词做哈希，只记录输入指纹，不记录提示词原文。 */
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash prompt", exception);
        }
    }

    /** 创建只写证据文件，避免误覆盖历史调用。 */
    private static void saveEvidence(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
