package org.practice.fundgateway.experiments.console;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.practice.fundgateway.experiments.console.ConsoleModels.GuardianSimulationRequest;
import org.practice.fundgateway.experiments.console.ConsoleModels.GuardianSimulationResponse;
import org.practice.fundgateway.guardian.diagnosis.ContractEvidence;
import org.practice.fundgateway.guardian.diagnosis.DeterministicDiagnosisService;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisEvidence;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisResult;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.IncidentEvidence;
import org.practice.fundgateway.guardian.diagnosis.MetricsEvidence;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.metrics.MetricEvent;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregate;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregator;
import org.practice.fundgateway.guardian.metrics.RiskCooldownGate;
import org.practice.fundgateway.guardian.metrics.RiskFingerprint;
import org.practice.fundgateway.guardian.metrics.RiskRuleEvaluator;
import org.practice.fundgateway.guardian.metrics.RiskRuleHit;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

/** 为控制台执行可复算的智能守护回放，并按需调用 DeepSeek。 */
@Service
public class GuardianConsoleService {

    private static final int EVENTS_PER_SECOND = 100;
    private static final Duration WINDOW_SIZE = Duration.ofSeconds(10);
    private static final Duration COOLDOWN = Duration.ofMinutes(1);
    private static final Instant BASE_TIME = Instant.parse("2026-09-11T10:00:00Z");
    private static final String MODEL = "deepseek-v4-flash";
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 执行指标生成、窗口聚合、规则判定、降频和可选模型诊断。 */
    public GuardianSimulationResponse simulate(GuardianSimulationRequest request) throws Exception {
        validate(request);
        long startedAt = System.nanoTime();
        Scenario scenario = Scenario.valueOf(request.scenario());
        int messageCount = request.messageCount();
        MetricWindowAggregator aggregator = new MetricWindowAggregator(WINDOW_SIZE);
        Map<Instant, Boolean> windowStarts = new LinkedHashMap<>();
        for (int index = 0; index < messageCount; index++) {
            MetricEvent event = scenario.event(index, BASE_TIME.plusMillis(index * 1000L / EVENTS_PER_SECOND));
            aggregator.accept(event);
            windowStarts.put(bucketStart(event.occurredAt()), Boolean.TRUE);
        }

        RiskRuleEvaluator evaluator = new RiskRuleEvaluator();
        RiskCooldownGate cooldown = new RiskCooldownGate(COOLDOWN);
        List<MetricWindowAggregate> windows = new ArrayList<>();
        List<RiskRuleHit> representativeHits = List.of();
        int riskWindows = 0;
        int diagnosticTasks = 0;
        String riskFingerprint = null;
        for (Instant windowStart : windowStarts.keySet()) {
            MetricWindowAggregate aggregate = aggregator.snapshot("fund-gateway", "/credit/apply", windowStart);
            windows.add(aggregate);
            List<RiskRuleHit> hits = evaluator.evaluate(aggregate);
            if (!hits.isEmpty()) {
                riskWindows++;
                representativeHits = hits;
                riskFingerprint = RiskFingerprint.of(aggregate, hits);
                if (cooldown.tryAcquire(riskFingerprint, windowStart)) {
                    diagnosticTasks++;
                }
            }
        }

        MetricWindowAggregate representative = windows.get(windows.size() - 1);
        DiagnosisResult deterministic = deterministicDiagnosis(representative, scenario);
        ModelOutcome model = invokeModelIfRequested(request, representative, deterministic,
                riskFingerprint, diagnosticTasks);
        return new GuardianSimulationResponse(scenario.name(), messageCount, windows.size(), riskWindows,
                diagnosticTasks, Math.max(0, riskWindows - diagnosticTasks), model.actualCalls(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), representative,
                representativeHits, deterministic.findings(), riskFingerprint, model.status(),
                model.prompt(), model.rawResponse(), model.report(), model.gateDecision(), Instant.now());
    }

    /** 返回当前进程是否能够进行真实模型调用。 */
    public boolean deepSeekAvailable() {
        String key = System.getenv("DEEPSEEK_API_KEY");
        return key != null && !key.isBlank();
    }

    /** 使用现有五条确定性诊断规则生成模型对照基线。 */
    private DiagnosisResult deterministicDiagnosis(MetricWindowAggregate aggregate, Scenario scenario) {
        MetricsEvidence metrics = new MetricsEvidence("synthetic-provider", "credit-apply",
                (int) Math.round(aggregate.qps()), scenario.averageLatencyMs,
                aggregate.timeoutRate(), (int) aggregate.activeThreads(), (int) aggregate.maxThreads());
        DiagnosisEvidence evidence = new DiagnosisEvidence(
                new ContractEvidence("synthetic-provider", "credit-apply", 100, 1000), metrics,
                new IncidentEvidence("synthetic-provider", "credit-apply", "incident-demo-001",
                        "CONFIRMED", "upstream-timeout", "2026-09-10T10:00:00Z"));
        return new DeterministicDiagnosisService().diagnose(evidence);
    }

    /** 用户明确选择后才调用一次模型，并执行结构与规则一致性门禁。 */
    private ModelOutcome invokeModelIfRequested(GuardianSimulationRequest request,
                                                MetricWindowAggregate aggregate,
                                                DiagnosisResult deterministic,
                                                String fingerprint,
                                                int diagnosticTasks) throws Exception {
        if (!Boolean.TRUE.equals(request.invokeModel())) {
            return ModelOutcome.skipped("SKIPPED");
        }
        if (diagnosticTasks == 0) {
            return ModelOutcome.skipped("NO_DIAGNOSTIC_TASK");
        }
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            return ModelOutcome.skipped("API_KEY_UNAVAILABLE");
        }
        MetricsEvidence metrics = new MetricsEvidence("synthetic-provider", "credit-apply",
                (int) Math.round(aggregate.qps()), (int) aggregate.p95LatencyMs(), aggregate.timeoutRate(),
                (int) aggregate.activeThreads(), (int) aggregate.maxThreads());
        DiagnosisSnapshot snapshot = new DiagnosisSnapshot("console-" + aggregate.windowStart().toEpochMilli(),
                Instant.now(), metrics, deterministic.findings(),
                new ContractEvidence("synthetic-provider", "credit-apply", 100, 1000),
                List.of(new DiagnosisSnapshot.RagCitation("console-rag-001",
                        "接口 QPS 上限为 100，超时时间为 1000 毫秒。",
                        "credit-application-guide", "v1", "授信申请#88-88")),
                fingerprint);
        String prompt = buildPrompt(snapshot);
        ChatCompletionRequest modelRequest = ChatCompletionRequest.builder().model(MODEL)
                .messages(List.of(new ChatCompletionMessage(prompt, Role.USER))).stream(false).build();
        var responseEntity = DeepSeekApi.builder().baseUrl("https://api.deepseek.com").apiKey(key).build()
                .chatCompletionEntity(modelRequest);
        var response = responseEntity.getBody();
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message().content() == null) {
            return new ModelOutcome("EMPTY_RESPONSE", 1, prompt,
                    response == null ? null : mapper.writeValueAsString(response), null, null);
        }
        String rawResponse = mapper.writeValueAsString(response);
        ModelDiagnosisReport report = mapper.readValue(response.choices().getFirst().message().content(),
                ModelDiagnosisReport.class);
        ModelDiagnosisGate.GateDecision gateDecision = new ModelDiagnosisGate().assess(report, snapshot);
        return new ModelOutcome("COMPLETED", 1, prompt, rawResponse, report, gateDecision);
    }

    /** 组装固定 JSON 输出和中文门禁要求。 */
    private String buildPrompt(DiagnosisSnapshot snapshot) throws Exception {
        return "你是 Java 应用故障诊断助手。只能依据诊断快照中的事实做只读分析，不得执行工具或修改操作。"
                + "只返回一个 JSON 对象，字段固定为 summary、riskLevel、findings、requiresHumanReview。"
                + "riskLevel 只能是 LOW、MEDIUM、HIGH；findings 每项必须包含 ruleId、matched、evidence、recommendation、requiresHumanReview。"
                + "ruleId 只能使用快照已有规则；summary、evidence、recommendation 必须使用简体中文。"
                + "不得输出 Markdown、解释文字或代码围栏；模型与 Java 规则冲突时 requiresHumanReview 必须为 true。"
                + "\n诊断快照：" + mapper.writeValueAsString(snapshot);
    }

    /** 计算事件所属十秒窗口起点。 */
    private Instant bucketStart(Instant occurredAt) {
        long seconds = WINDOW_SIZE.getSeconds();
        return Instant.ofEpochSecond(occurredAt.getEpochSecond()
                - Math.floorMod(occurredAt.getEpochSecond(), seconds));
    }

    /** 校验场景、消息数量和模型开关。 */
    private void validate(GuardianSimulationRequest request) {
        if (request == null || request.scenario() == null) {
            throw new IllegalArgumentException("守护场景不能为空");
        }
        try {
            Scenario.valueOf(request.scenario());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知守护场景：" + request.scenario());
        }
        if (request.messageCount() == null
                || !List.of(100, 1000, 10000).contains(request.messageCount())) {
            throw new IllegalArgumentException("消息数量只能是 100、1000 或 10000");
        }
    }

    /** 四类合成指标场景，参数均可由结果反算。 */
    private enum Scenario {
        NORMAL(0, 0, 10, 50, 100, List.of(80L, 100L, 120L)),
        ERROR_RATE(25, 2, 26, 50, 180, List.of(120L, 160L, 200L)),
        LATENCY(2, 1, 32, 50, 850, List.of(600L, 850L, 1100L)),
        COMBINED(25, 15, 48, 50, 900, List.of(700L, 900L, 1300L));

        private final int errorPercent;
        private final int timeoutPercent;
        private final int activeThreads;
        private final int maxThreads;
        private final int averageLatencyMs;
        private final List<Long> latencies;

        /** 保存场景生成指标所需的确定性参数。 */
        Scenario(int errorPercent, int timeoutPercent, int activeThreads, int maxThreads,
                 int averageLatencyMs, List<Long> latencies) {
            this.errorPercent = errorPercent;
            this.timeoutPercent = timeoutPercent;
            this.activeThreads = activeThreads;
            this.maxThreads = maxThreads;
            this.averageLatencyMs = averageLatencyMs;
            this.latencies = latencies;
        }

        /** 根据百分比生成一条确定性的合成指标消息。 */
        private MetricEvent event(int index, Instant occurredAt) {
            long error = index % 100 < errorPercent ? 1 : 0;
            long timeout = index % 100 < timeoutPercent ? 1 : 0;
            return new MetricEvent("console-event-" + index, occurredAt, "fund-gateway",
                    "/credit/apply", 1, error, timeout, activeThreads, maxThreads,
                    this == COMBINED ? 1 : 0, this == COMBINED ? 18 : 0, latencies);
        }
    }

    /** 封装真实模型调用结果和未调用原因。 */
    private record ModelOutcome(String status, int actualCalls, String prompt, String rawResponse,
                                ModelDiagnosisReport report,
                                ModelDiagnosisGate.GateDecision gateDecision) {

        /** 创建未调用模型的结果。 */
        private static ModelOutcome skipped(String status) {
            return new ModelOutcome(status, 0, null, null, null, null);
        }
    }
}
