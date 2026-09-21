package org.practice.fundgateway.experiments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticSnapshotAssembler;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** M5 端到端模拟：读取数据库待诊断任务，调用模型并执行 Java 门禁。 */
@Component
@Profile("m5-e2e")
public class M5DatabaseDiagnosisExperiment implements CommandLineRunner {

    private static final String MODEL = "deepseek-v4-flash";
    private static final String BASE_URL = "https://api.deepseek.com";
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 启动后消费一条待诊断任务，完成一次可复现的端到端诊断。 */
    @Override
    public void run(String... args) throws Exception {
        execute();
    }

    /** 执行数据库任务、模型调用、报告校验和状态回写。 */
    private void execute() throws Exception {
        String key = requireEnv("DEEPSEEK_API_KEY");
        JdbcTemplate jdbc = createJdbcTemplate();
        GuardianRiskRepository repository = new GuardianRiskRepository(jdbc);
        repository.ensureSchema();
        DiagnosticSnapshotAssembler assembler = new DiagnosticSnapshotAssembler(repository);
        DiagnosisSnapshot snapshot = assembler.assemblePending()
                .orElseThrow(() -> new IllegalStateException("没有待诊断任务"));

        Path evidenceDir = Path.of("docs", "learning", "M5-database-e2e-" + System.currentTimeMillis());
        Files.createDirectories(evidenceDir);
        String prompt = buildPrompt(snapshot);
        ChatCompletionRequest request = ChatCompletionRequest.builder().model(MODEL)
                .messages(List.of(new ChatCompletionMessage(prompt, Role.USER))).stream(false).build();
        save(evidenceDir.resolve("snapshot.json"), mapper.writeValueAsString(snapshot));
        save(evidenceDir.resolve("request.json"), mapper.writeValueAsString(request));

        DeepSeekApi api = DeepSeekApi.builder().baseUrl(BASE_URL).apiKey(key).build();
        var responseEntity = invokeModel(repository, snapshotTaskId(snapshot), evidenceDir,
                () -> api.chatCompletionEntity(request));
        var response = responseEntity.getBody();
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message().content() == null) {
            repository.updateDiagnosticTaskStatus(snapshotTaskId(snapshot), "FAILED");
            throw new IllegalStateException("模型返回为空");
        }
        String rawResponse = mapper.writeValueAsString(response);
        String content = response.choices().getFirst().message().content();
        save(evidenceDir.resolve("response.json"), rawResponse);
        save(evidenceDir.resolve("model-content.json"), content);

        ModelDiagnosisReport report;
        ModelDiagnosisGate.GateDecision decision;
        try {
            report = mapper.readValue(content, ModelDiagnosisReport.class);
            decision = new ModelDiagnosisGate().assess(report, snapshot);
        } catch (RuntimeException parseOrGateFailure) {
            repository.updateDiagnosticTaskStatus(snapshotTaskId(snapshot), "FAILED");
            save(evidenceDir.resolve("gate-decision.json"), mapper.writeValueAsString(
                    new ModelDiagnosisGate.GateDecision(ModelDiagnosisGate.GateStatus.HUMAN_REVIEW,
                            "模型报告解析或门禁失败：" + parseOrGateFailure.getMessage())));
            throw parseOrGateFailure;
        }
        save(evidenceDir.resolve("validated-model-report.json"), mapper.writeValueAsString(report));
        save(evidenceDir.resolve("gate-decision.json"), mapper.writeValueAsString(decision));
        repository.updateDiagnosticTaskStatus(snapshotTaskId(snapshot),
                decision.status() == ModelDiagnosisGate.GateStatus.ACCEPTED ? "COMPLETED" : "HUMAN_REVIEW");
        System.out.println("M5 数据库端到端诊断完成：task=" + snapshot.snapshotId()
                + "，门禁=" + decision.status() + "，证据目录=" + evidenceDir);
    }

    /** 快照标识就是数据库诊断任务 UUID，转换失败时让流程明确失败。 */
    private java.util.UUID snapshotTaskId(DiagnosisSnapshot snapshot) {
        return java.util.UUID.fromString(snapshot.snapshotId());
    }

    /** 创建与 M4 一致的本地 PostgreSQL 连接。 */
    private JdbcTemplate createJdbcTemplate() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
        dataSource.setUser("fund_demo");
        dataSource.setPassword("fund_demo_password");
        return new JdbcTemplate(dataSource);
    }

    /** 组装固定 JSON 输出要求，模型只负责解释快照事实。 */
    private String buildPrompt(DiagnosisSnapshot snapshot) throws Exception {
        return "你是 Java 应用故障诊断助手。只能依据诊断快照中的事实做只读分析，不得执行工具或修改操作。"
                + "只返回一个 JSON 对象，字段固定为 summary、riskLevel、findings、requiresHumanReview。"
                + "riskLevel 只能是 LOW、MEDIUM、HIGH；findings 每项必须包含 ruleId、matched、evidence、recommendation、requiresHumanReview。"
                + "ruleId 只能使用快照已有规则；summary、evidence、recommendation 必须使用简体中文。"
                + "不得输出 Markdown、解释文字或代码围栏；模型与 Java 规则冲突时 requiresHumanReview 必须为 true。"
                + "\n诊断快照：" + mapper.writeValueAsString(snapshot);
    }

    /** 只读取密钥，不打印密钥内容。 */
    private String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    /** 保存原始证据文件。 */
    private void save(Path path, String content) throws Exception {
        Files.writeString(path, content);
    }

    /** 模型网络调用失败时先停止重复消费并保存不含密钥的失败类型。 */
    static <T> T invokeModel(GuardianRiskRepository repository, UUID taskId, Path evidenceDir,
                             Callable<T> invocation) throws Exception {
        try {
            return invocation.call();
        } catch (Exception modelFailure) {
            try {
                repository.updateDiagnosticTaskStatus(taskId, "FAILED");
            } catch (RuntimeException statusFailure) {
                modelFailure.addSuppressed(statusFailure);
            }
            try {
                Files.writeString(evidenceDir.resolve("model-failure.txt"),
                        modelFailure.getClass().getSimpleName());
            } catch (Exception evidenceFailure) {
                modelFailure.addSuppressed(evidenceFailure);
            }
            throw modelFailure;
        }
    }
}
