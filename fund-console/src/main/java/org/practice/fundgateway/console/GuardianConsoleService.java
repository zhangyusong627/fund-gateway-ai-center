package org.practice.fundgateway.console;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.practice.fundgateway.console.ConsoleModels.GuardianSimulationRequest;
import org.practice.fundgateway.console.ConsoleModels.GuardianSimulationResponse;
import org.practice.fundgateway.common.permission.PermissionAuditRecorder;
import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.common.permission.PermissionDeniedException;
import org.practice.fundgateway.common.permission.PermissionGuard;
import org.practice.fundgateway.guardian.diagnosis.ContractEvidence;
import org.practice.fundgateway.guardian.diagnosis.DeterministicDiagnosisService;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisEvidence;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisResult;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.IncidentEvidence;
import org.practice.fundgateway.guardian.diagnosis.MetricsEvidence;
import org.practice.fundgateway.guardian.ai.ModelDiagnosisFacade;
import org.practice.fundgateway.guardian.ai.ModelGateway;
import org.practice.fundgateway.guardian.audit.InMemoryModelCallAuditRepository;
import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.practice.fundgateway.guardian.metrics.MetricEvent;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregate;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregator;
import org.practice.fundgateway.guardian.metrics.RiskCooldownGate;
import org.practice.fundgateway.guardian.metrics.RiskFingerprint;
import org.practice.fundgateway.guardian.metrics.RiskRuleEvaluator;
import org.practice.fundgateway.guardian.metrics.RiskRuleHit;
import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView;
import org.practice.fundgateway.guardian.workflow.DiagnosticWorkflowService;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

/** 为控制台执行可复算的智能守护回放，并按需调用 DeepSeek。 */
@Service
public class GuardianConsoleService {

    private static final int EVENTS_PER_SECOND = 100;
    private static final Duration WINDOW_SIZE = Duration.ofSeconds(10);
    private static final Duration COOLDOWN = Duration.ofMinutes(1);
    private static final Instant BASE_TIME = Instant.parse("2026-09-11T10:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final DiagnosticWorkflowService workflowService;
    private final ConsoleRagService ragService;
    private final GuardianRiskRepository riskRepository;
    private final ModelDiagnosisFacade modelFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionContext permissionContext;

    /** 注入诊断任务、人工审批和模拟治理工作流。 */
    public GuardianConsoleService(DiagnosticWorkflowService workflowService) {
        this(workflowService, null, null,
                new ModelDiagnosisFacade(ModelGateway.unavailable(),
                        new ModelAuditApplicationService(new InMemoryModelCallAuditRepository())),
                new PermissionGuard(PermissionAuditRecorder.noop()), PermissionContext.syntheticConsole());
    }

    /** 注入正式 RAG 证据查询和模型审计能力。 */
    public GuardianConsoleService(DiagnosticWorkflowService workflowService, ConsoleRagService ragService,
                                   GuardianRiskRepository riskRepository, ModelDiagnosisFacade modelFacade) {
        this(workflowService, ragService, riskRepository, modelFacade,
                new PermissionGuard(PermissionAuditRecorder.noop()), PermissionContext.syntheticConsole());
    }

    /** 注入正式 RAG、风险、模型和权限审计能力。 */
    @org.springframework.beans.factory.annotation.Autowired
    public GuardianConsoleService(DiagnosticWorkflowService workflowService, ConsoleRagService ragService,
                                   GuardianRiskRepository riskRepository, ModelDiagnosisFacade modelFacade,
                                   PermissionGuard permissionGuard, PermissionContext permissionContext) {
        this.workflowService = workflowService;
        this.ragService = ragService;
        this.riskRepository = riskRepository;
        this.modelFacade = modelFacade;
        this.permissionGuard = permissionGuard;
        this.permissionContext = permissionContext;
    }

    /** 执行指标生成、窗口聚合、规则判定、降频和可选模型诊断。 */
    public GuardianSimulationResponse simulate(GuardianSimulationRequest request) throws Exception {
        return simulate(request, permissionContext);
    }

    /** 使用指定权限上下文执行诊断回放，便于验证越权请求在业务入口被拒绝。 */
    public GuardianSimulationResponse simulate(GuardianSimulationRequest request,
                                                PermissionContext requestContext) throws Exception {
        validate(request);
        String replayId = UUID.randomUUID().toString();
        permissionGuard.requireProvider(requestContext, "synthetic-provider", "GUARDIAN_DIAGNOSTIC_READ",
                "console-guardian-" + replayId);
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
        Map<String, RiskCandidate> admittedCandidates = new LinkedHashMap<>();
        int riskWindows = 0;
        int cooldownAdmittedWindows = 0;
        for (Instant windowStart : windowStarts.keySet()) {
            MetricWindowAggregate aggregate = aggregator.snapshot("fund-gateway", "/credit/apply", windowStart);
            windows.add(aggregate);
            List<RiskRuleHit> hits = evaluator.evaluate(aggregate);
            if (!hits.isEmpty()) {
                riskWindows++;
                String evaluatedFingerprint = RiskFingerprint.of(aggregate, hits);
                if (cooldown.tryAcquire(evaluatedFingerprint, windowStart)) {
                    cooldownAdmittedWindows++;
                    admittedCandidates.putIfAbsent(evaluatedFingerprint,
                            new RiskCandidate(aggregate, hits, evaluatedFingerprint));
                }
            }
        }

        RiskCandidate primaryCandidate = admittedCandidates.values().stream().findFirst()
                .orElse(new RiskCandidate(windows.get(0), List.of(), null));
        MetricWindowAggregate representative = primaryCandidate.aggregate();
        DiagnosisResult deterministic = deterministicDiagnosis(representative, scenario);
        ModelDiagnosisFacade.DiagnosisOutcome model = ModelDiagnosisFacade.DiagnosisOutcome.skipped("NO_DIAGNOSTIC_TASK");
        DiagnosticTaskView diagnosticTask = null;
        int diagnosticTasks = 0;
        int actualModelCalls = 0;
        for (RiskCandidate candidate : admittedCandidates.values()) {
            DiagnosticTaskView activeTask = workflowService.findActiveByRiskFingerprint(candidate.fingerprint()).orElse(null);
            DiagnosisResult candidateDeterministic = deterministicDiagnosis(candidate.aggregate(), scenario);
            ModelDiagnosisFacade.DiagnosisOutcome candidateModel = activeTask == null
                    ? invokeModelIfRequested(request, candidate.aggregate(), candidateDeterministic,
                            candidate.fingerprint(), 1, "console-" + scenario.name() + "-" + replayId,
                            requestContext)
                    : ModelDiagnosisFacade.DiagnosisOutcome.skipped("ACTIVE_TASK_EXISTS");
            DiagnosticTaskView candidateTask = activeTask != null ? activeTask
                    : createWorkflowTask(scenario, candidateModel, candidate.aggregate(), candidate.hits(),
                            replayId, requestContext);
            actualModelCalls += candidateModel.actualCalls();
            if (candidateTask != null) {
                diagnosticTasks++;
            }
            if (diagnosticTask == null && candidateTask != null) {
                diagnosticTask = candidateTask;
                model = candidateModel;
                deterministic = candidateDeterministic;
            } else if (diagnosticTask == null && model.status().equals("NO_DIAGNOSTIC_TASK")) {
                model = candidateModel;
                deterministic = candidateDeterministic;
            }
        }
        String riskFingerprint = primaryCandidate.fingerprint();
        List<RiskRuleHit> representativeHits = primaryCandidate.hits();
        return new GuardianSimulationResponse(scenario.name(), messageCount, windows.size(), riskWindows,
                diagnosticTasks, Math.max(0, riskWindows - cooldownAdmittedWindows), actualModelCalls,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), representative,
                representativeHits, deterministic.findings(), riskFingerprint, model.status(),
                model.rawRequest(), model.prompt(), model.rawResponse(), model.report(), model.gateDecision(), diagnosticTask,
                Instant.now());
    }

    /** 表示一个通过规则和冷却、等待独立诊断的风险候选。 */
    private record RiskCandidate(MetricWindowAggregate aggregate, List<RiskRuleHit> hits, String fingerprint) {
    }

    /** 先落风险事件再创建工作流，保证 PostgreSQL 外键和审计链完整。 */
    private DiagnosticTaskView createWorkflowTask(Scenario scenario, ModelDiagnosisFacade.DiagnosisOutcome model,
                                                   MetricWindowAggregate aggregate,
                                                   List<RiskRuleHit> hits, String replayId,
                                                   PermissionContext requestContext) throws Exception {
        if (model.report() == null) {
            return null;
        }
        if (riskRepository != null && model.snapshot() != null && model.snapshot().riskFingerprint() != null) {
            riskRepository.saveRiskEvent(new GuardianRiskRepository.RiskEventRecord(
                    model.snapshot().riskFingerprint(), "fund-gateway", "/credit/apply", "HIGH",
                    hits.stream().map(RiskRuleHit::ruleId).toList(), aggregate.windowStart(),
                    aggregate.windowStart().plus(WINDOW_SIZE), Instant.now(), mapper.writeValueAsString(aggregate)));
        }
        return workflowService.create("console-" + scenario.name() + "-" + BASE_TIME + "-" + replayId,
                model.snapshot(), model.report(), requestContext);
    }

    /** 返回当前进程是否能够进行真实模型调用。 */
    public boolean deepSeekAvailable() {
        return modelFacade.available();
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
    private ModelDiagnosisFacade.DiagnosisOutcome invokeModelIfRequested(GuardianSimulationRequest request,
                                                MetricWindowAggregate aggregate,
                                                DiagnosisResult deterministic,
                                                String fingerprint,
                                                int diagnosticTasks, String traceId,
                                                PermissionContext requestContext) throws Exception {
        if (!Boolean.TRUE.equals(request.invokeModel())) {
            return ModelDiagnosisFacade.DiagnosisOutcome.skipped("SKIPPED");
        }
        if (diagnosticTasks == 0) {
            return ModelDiagnosisFacade.DiagnosisOutcome.skipped("NO_DIAGNOSTIC_TASK");
        }
        if (!modelFacade.available()) {
            return ModelDiagnosisFacade.DiagnosisOutcome.skipped("MODEL_UNAVAILABLE");
        }
        MetricsEvidence metrics = new MetricsEvidence("synthetic-provider", "credit-apply",
                (int) Math.round(aggregate.qps()), (int) aggregate.p95LatencyMs(), aggregate.timeoutRate(),
                (int) aggregate.activeThreads(), (int) aggregate.maxThreads());
        List<DiagnosisSnapshot.RagCitation> citations = loadRagCitations(requestContext, traceId);
        if (citations.isEmpty()) {
            return ModelDiagnosisFacade.DiagnosisOutcome.skipped("RAG_EVIDENCE_UNAVAILABLE");
        }
        DiagnosisSnapshot snapshot = new DiagnosisSnapshot(traceId,
                Instant.now(), metrics, deterministic.findings(),
                new ContractEvidence("synthetic-provider", "credit-apply", 100, 1000),
                citations,
                fingerprint);
        return modelFacade.diagnose(snapshot, traceId);
    }

    /** 从本次在线检索结果生成诊断引用，禁止使用展示层硬编码证据。 */
    private List<DiagnosisSnapshot.RagCitation> loadRagCitations(PermissionContext requestContext, String traceId)
            throws Exception {
        if (ragService == null) {
            permissionGuard.requireKnowledge(requestContext, "*", "synthetic", "v1",
                    "DIAGNOSTIC_EVIDENCE_READ", traceId);
            return List.of(new DiagnosisSnapshot.RagCitation("synthetic-fallback",
                    "接口 QPS 上限为 100，超时时间为 1000 毫秒。", "synthetic", "v1", "授信申请"));
        }
        try {
            var response = ragService.query(new ConsoleModels.RagQueryRequest("fund-gateway-contracts", null, null,
                    "授信申请金额字段的类型和必填要求是什么？", List.of("applyAmt", "BigDecimal", "必填"), 3),
                    requestContext);
            if (!"ACCEPTED".equals(response.status())) {
                return List.of();
            }
            return response.candidates().stream().map(candidate -> new DiagnosisSnapshot.RagCitation(
                    candidate.chunkId(), candidate.content(), candidate.documentId(),
                    candidate.documentVersion(), candidate.locator())).toList();
        } catch (PermissionDeniedException exception) {
            throw exception;
        } catch (Exception exception) {
            return List.of();
        }
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

}
