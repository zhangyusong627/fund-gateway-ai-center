package org.practice.fundgateway.experiments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.practice.fundgateway.guardian.diagnosis.ContractEvidence;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.MetricsEvidence;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.ModelFinding;
import org.practice.fundgateway.guardian.diagnosis.RuleFinding;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** M5 真实诊断回放：固定 JSON 输出并执行 Java 最终门禁。 */
@Component
@Profile("m5-real")
public class M5DeepSeekDiagnosisExperiment implements CommandLineRunner {

    private static final String MODEL = "deepseek-v4-flash";
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Spring Boot 启动后执行一次合成诊断回放。 */
    @Override
    public void run(String... args) throws Exception {
        execute();
    }

    /** 调用模型、解析固定 JSON，并将冲突交给人工审核状态。 */
    private void execute() throws Exception {
        String key = requireEnv("DEEPSEEK_API_KEY");
        Path evidenceDir = Path.of("docs", "learning", "M5-real-call-" + System.currentTimeMillis());
        Files.createDirectories(evidenceDir);

        DiagnosisSnapshot snapshot = snapshot();
        String prompt = buildPrompt(snapshot);
        ChatCompletionRequest request = ChatCompletionRequest.builder().model(MODEL)
                .messages(List.of(new ChatCompletionMessage(prompt, Role.USER))).stream(false).build();
        save(evidenceDir.resolve("request.json"), mapper.writeValueAsString(request));

        DeepSeekApi api = DeepSeekApi.builder().baseUrl("https://api.deepseek.com").apiKey(key).build();
        var response = api.chatCompletionEntity(request).getBody();
        String rawResponse = mapper.writeValueAsString(response);
        save(evidenceDir.resolve("response.json"), rawResponse);
        String content = response.choices().getFirst().message().content();
        save(evidenceDir.resolve("model-content.json"), content);

        ModelDiagnosisReport report = mapper.readValue(content, ModelDiagnosisReport.class);
        ModelDiagnosisGate.GateDecision decision = new ModelDiagnosisGate().assess(report, snapshot);
        save(evidenceDir.resolve("gate-decision.json"), mapper.writeValueAsString(decision));
        System.out.println("M5 真实诊断完成，门禁状态=" + decision.status() + "，证据目录=" + evidenceDir);
    }

    /** 构造本次回放使用的合成快照，模拟 M4 已产生的风险窗口。 */
    private DiagnosisSnapshot snapshot() {
        MetricsEvidence metrics = new MetricsEvidence("synthetic-provider", "credit-apply", 95, 820,
                0.18, 48, 50);
        List<RuleFinding> rules = List.of(
                new RuleFinding("R001", true, "HIGH", "当前 QPS=95，契约上限=100", "降低调用并发", true),
                new RuleFinding("R002", true, "HIGH", "超时率=0.18", "检查上游超时", true),
                new RuleFinding("R003", true, "MEDIUM", "平均响应=820ms，契约超时=1000ms", "核查上游延迟", true),
                new RuleFinding("R004", true, "HIGH", "线程池活跃数=48/50", "检查线程池容量", true));
        return new DiagnosisSnapshot("m5-snapshot-001", Instant.now(), metrics, rules,
                new ContractEvidence("synthetic-provider", "credit-apply", 100, 1000),
                List.of(new DiagnosisSnapshot.RagCitation("rag-001", "接口 QPS 上限为 100", "doc-001", "v1", "第 3 节")),
                "synthetic-fingerprint-001");
    }

    /** 生成固定 JSON 字段和语言约束，禁止模型输出 Markdown。 */
    private String buildPrompt(DiagnosisSnapshot snapshot) throws Exception {
        String facts = mapper.writeValueAsString(snapshot);
        return "你是 Java 应用故障诊断助手。只能依据下面的合成诊断快照作只读分析，不得执行任何工具或修改操作。"
                + "必须只返回一个 JSON 对象，不得输出 Markdown、解释文字或代码围栏。JSON 字段固定为："
                + "summary（字符串）、riskLevel（LOW/MEDIUM/HIGH）、findings（数组）、requiresHumanReview（布尔）。"
                + "findings 每项固定包含 ruleId、matched、evidence、recommendation、requiresHumanReview；"
                + "ruleId 只能是 R001、R002、R003、R004、R005；summary、evidence、recommendation 必须使用简体中文。"
                + "模型建议必须引用快照中的事实；如果与规则结论冲突，requiresHumanReview 必须为 true。\n诊断快照：" + facts;
    }

    /** 读取密钥但不输出密钥内容。 */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    /** 保存原始请求、响应或门禁结果。 */
    private static void save(Path path, String content) throws Exception {
        Files.writeString(path, content);
        System.out.println(path + " 已保存");
    }
}
