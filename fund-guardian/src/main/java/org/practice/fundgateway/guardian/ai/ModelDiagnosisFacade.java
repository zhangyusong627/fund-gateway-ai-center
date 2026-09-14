package org.practice.fundgateway.guardian.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.practice.fundgateway.guardian.audit.ModelCallAudit;
import org.practice.fundgateway.guardian.audit.ModelCallStatus;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.memory.ClasspathPromptTemplateRepository;

import tools.jackson.databind.json.JsonMapper;

/** 统一编排模型调用、结构化输出、重试、超时、门禁和调用审计。 */
public class ModelDiagnosisFacade implements AutoCloseable {

    private static final String PROVIDER = "deepseek";
    private static final String MODEL = "deepseek-v4-flash";
    private static final String PROMPT_VERSION = "guardian-diagnosis-v1";
    private static final int MAX_OUTPUT_TOKENS = 1200;
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final ModelGateway gateway;
    private final ModelAuditApplicationService auditService;
    private final JsonMapper mapper;
    private final ModelDiagnosisGate gate;
    private final Duration timeout;
    private final int maxAttempts;
    private final ExecutorService executor;
    private final Clock clock;
    private final ClasspathPromptTemplateRepository promptRepository;

    /** 使用固定模型治理策略创建正式 Facade。 */
    public ModelDiagnosisFacade(ModelGateway gateway, ModelAuditApplicationService auditService) {
        this(gateway, auditService, JsonMapper.builder().build(), new ModelDiagnosisGate(), DEFAULT_TIMEOUT,
                DEFAULT_MAX_ATTEMPTS, Executors.newVirtualThreadPerTaskExecutor(), Clock.systemUTC());
    }

    /** 注入策略和执行器，便于测试超时、重试和门禁分支。 */
    public ModelDiagnosisFacade(ModelGateway gateway, ModelAuditApplicationService auditService,
                                JsonMapper mapper, ModelDiagnosisGate gate, Duration timeout, int maxAttempts,
                                ExecutorService executor, Clock clock) {
        if (gateway == null || auditService == null || mapper == null || gate == null || timeout == null
                || timeout.isZero() || timeout.isNegative() || maxAttempts < 1 || executor == null || clock == null) {
            throw new IllegalArgumentException("模型 Facade 配置不完整");
        }
        this.gateway = gateway;
        this.auditService = auditService;
        this.mapper = mapper;
        this.gate = gate;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
        this.executor = executor;
        this.clock = clock;
        this.promptRepository = new ClasspathPromptTemplateRepository();
    }

    /** 返回模型适配器是否可用，调用方据此决定是否展示真实调用入口。 */
    public boolean available() {
        return gateway.available();
    }

    /** 执行一次受控诊断；同一逻辑调用最多使用固定次数的基础设施尝试。 */
    public DiagnosisOutcome diagnose(DiagnosisSnapshot snapshot, String traceId) {
        if (snapshot == null || traceId == null || traceId.isBlank()) {
            return DiagnosisOutcome.skipped("INVALID_REQUEST");
        }
        if (!available()) {
            return DiagnosisOutcome.skipped("MODEL_UNAVAILABLE");
        }
        try {
            String prompt = buildPrompt(snapshot);
            String rawRequest = mapper.writeValueAsString(Map.of(
                    "model", MODEL,
                    "stream", false,
                    "max_tokens", MAX_OUTPUT_TOKENS,
                    "messages", List.of(Map.of("role", "user", "content", prompt))));
            ModelGateway.ModelRequest request = new ModelGateway.ModelRequest(MODEL, prompt, MAX_OUTPUT_TOKENS,
                    rawRequest);
            return callWithPolicy(request, snapshot, traceId, prompt);
        } catch (Exception exception) {
            return new DiagnosisOutcome("FAILED", 0, 0, null, null, null, null, null, snapshot);
        }
    }

    /** 释放 Java 21 虚拟线程执行器，避免应用停止时遗留模型调用任务。 */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    /** 执行有限重试，并把最终状态统一写入一条幂等审计记录。 */
    private DiagnosisOutcome callWithPolicy(ModelGateway.ModelRequest request, DiagnosisSnapshot snapshot,
                                            String traceId, String prompt) {
        String rawResponse = "";
        int attempts = 0;
        long logicalStarted = System.nanoTime();
        while (attempts < maxAttempts) {
            attempts++;
            boolean structuredPhase = false;
            try {
                ModelGateway.ModelCompletion completion = callWithTimeout(request);
                rawResponse = completion.rawResponse();
                structuredPhase = true;
                ModelDiagnosisReport report = mapper.readValue(completion.content(), ModelDiagnosisReport.class);
                ModelDiagnosisGate.GateDecision decision = gate.assess(report, snapshot);
                ModelCallStatus auditStatus = decision.status() == ModelDiagnosisGate.GateStatus.ACCEPTED
                        ? ModelCallStatus.SUCCEEDED : ModelCallStatus.REJECTED_BY_GATE;
                recordAudit(request.rawRequest(), rawResponse, traceId, logicalStarted, auditStatus, attempts - 1);
                String resultStatus = decision.status() == ModelDiagnosisGate.GateStatus.ACCEPTED
                        ? "COMPLETED" : "HUMAN_REVIEW";
                return new DiagnosisOutcome(resultStatus, attempts, attempts - 1, request.rawRequest(), prompt,
                        rawResponse, report, decision, snapshot);
            } catch (Exception exception) {
                Throwable cause = unwrap(exception);
                boolean timeoutFailure = cause instanceof TimeoutException;
                boolean retryable = timeoutFailure || gateway.retryable(cause)
                        || cause instanceof ModelGatewayException modelFailure && modelFailure.retryable();
                if (retryable && attempts < maxAttempts) {
                    continue;
                }
                ModelCallStatus auditStatus = timeoutFailure ? ModelCallStatus.TIMEOUT
                        : structuredPhase || isGateFailure(cause) ? ModelCallStatus.REJECTED_BY_GATE : ModelCallStatus.FAILED;
                recordAudit(request.rawRequest(), rawResponse, traceId, logicalStarted, auditStatus, attempts - 1);
                String resultStatus = auditStatus == ModelCallStatus.TIMEOUT ? "TIMEOUT"
                        : auditStatus == ModelCallStatus.REJECTED_BY_GATE ? "REJECTED_BY_GATE" : "FAILED";
                ModelDiagnosisGate.GateDecision decision = structuredPhase || isGateFailure(cause)
                        ? new ModelDiagnosisGate.GateDecision(ModelDiagnosisGate.GateStatus.HUMAN_REVIEW,
                        "模型结构化输出未通过门禁") : null;
                return new DiagnosisOutcome(resultStatus, attempts, attempts - 1, request.rawRequest(), prompt,
                        rawResponse, null, decision, snapshot);
            }
        }
        return new DiagnosisOutcome("FAILED", attempts, Math.max(0, attempts - 1), request.rawRequest(), prompt,
                rawResponse, null, null, snapshot);
    }

    /** 在固定时限内等待基础设施响应，超时后取消对应任务。 */
    private ModelGateway.ModelCompletion callWithTimeout(ModelGateway.ModelRequest request) throws Exception {
        Future<ModelGateway.ModelCompletion> future = executor.submit(() -> gateway.complete(request));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new IllegalStateException("模型适配器执行失败", cause);
        }
    }

    /** 组装固定版本的中文结构化诊断提示词，只传入 Java 已组装的快照。 */
    private String buildPrompt(DiagnosisSnapshot snapshot) throws Exception {
        return promptRepository.load("diagnosis-system", "v1").content()
                + "\n诊断快照：" + mapper.writeValueAsString(snapshot);
    }

    /** 对结构化门禁异常和普通基础设施异常做安全分类。 */
    private boolean isGateFailure(Throwable exception) {
        return exception instanceof IllegalArgumentException || exception instanceof ClassCastException;
    }

    /** 解开 Future 和适配器包装，保留真正异常类型用于策略判断。 */
    private Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        while ((current instanceof ExecutionException || current instanceof java.util.concurrent.CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** 估算 token 并保存请求、响应、状态和调用时价格快照。 */
    private void recordAudit(String rawRequest, String rawResponse, String traceId, long started,
                             ModelCallStatus status, int retryCount) {
        long inputTokens = Math.max(1, rawRequest.length() / 2L);
        long outputTokens = rawResponse == null ? 0 : rawResponse.length() / 2L;
        DeepSeekPricingPolicy.PriceSnapshot pricing = DeepSeekPricingPolicy.snapshot(MODEL);
        ModelCallAudit audit = ModelCallAudit.priced("model-call-" + UUID.randomUUID(), traceId, "guardian",
                "diagnosis", PROVIDER, MODEL, PROMPT_VERSION, inputTokens, outputTokens,
                Duration.ofNanos(System.nanoTime() - started).toMillis(), status, retryCount,
                pricing.version(), pricing.inputCacheMissPerMillion(), pricing.outputPerMillion(), pricing.currency(), rawRequest,
                rawResponse == null ? "" : rawResponse, Instant.now(clock));
        auditService.record(audit);
    }

    /** 封装模型调用结果，供控制台和工作流复用而不暴露 SDK 类型。 */
    public record DiagnosisOutcome(String status, int actualCalls, int retryCount, String rawRequest,
                                   String prompt, String rawResponse, ModelDiagnosisReport report,
                                   ModelDiagnosisGate.GateDecision gateDecision, DiagnosisSnapshot snapshot) {

        /** 创建未发起外部请求的结果。 */
        public static DiagnosisOutcome skipped(String status) {
            return new DiagnosisOutcome(status, 0, 0, null, null, null, null, null, null);
        }
    }
}
