package org.practice.fundgateway.guardian.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.guardian.audit.InMemoryModelCallAuditRepository;
import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.practice.fundgateway.guardian.audit.ModelCallStatus;
import org.practice.fundgateway.guardian.diagnosis.ContractEvidence;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.ModelFinding;
import org.practice.fundgateway.guardian.diagnosis.MetricsEvidence;
import org.practice.fundgateway.guardian.diagnosis.RuleFinding;

import tools.jackson.databind.json.JsonMapper;

/** 验证模型 Facade 的结构化输出、有限重试、超时停止和审计状态。 */
class ModelDiagnosisFacadeTest {

    /** 可重试的基础设施失败后应成功，并把重试次数写入审计。 */
    @Test
    void shouldRetryTransientFailureOnceAndAuditLogicalCall() {
        AtomicInteger calls = new AtomicInteger();
        ModelGateway gateway = gateway(() -> {
            if (calls.getAndIncrement() == 0) {
                throw new ModelGatewayException("temporary", null, true);
            }
            return validCompletion();
        });
        InMemoryModelCallAuditRepository repository = new InMemoryModelCallAuditRepository();

        try (ModelDiagnosisFacade facade = facade(gateway, repository, Duration.ofSeconds(1), 2)) {
            ModelDiagnosisFacade.DiagnosisOutcome outcome = facade.diagnose(snapshot(), "trace-retry");

            assertEquals("COMPLETED", outcome.status());
            assertEquals(2, outcome.actualCalls());
            assertEquals(1, outcome.retryCount());
            assertEquals(ModelCallStatus.SUCCEEDED, repository.findAll().getFirst().status());
            assertEquals(1, repository.findAll().getFirst().retryCount());
        }
    }

    /** 连续超时应在固定尝试预算耗尽后停止，并记录 TIMEOUT 而不是伪造报告。 */
    @Test
    void shouldStopAfterTimeoutBudget() {
        AtomicInteger calls = new AtomicInteger();
        ModelGateway gateway = gateway(() -> {
            calls.incrementAndGet();
            throw new TimeoutException("timeout");
        });
        InMemoryModelCallAuditRepository repository = new InMemoryModelCallAuditRepository();

        try (ModelDiagnosisFacade facade = facade(gateway, repository, Duration.ofSeconds(1), 2)) {
            ModelDiagnosisFacade.DiagnosisOutcome outcome = facade.diagnose(snapshot(), "trace-timeout");

            assertEquals("TIMEOUT", outcome.status());
            assertEquals(2, calls.get());
            assertEquals(ModelCallStatus.TIMEOUT, repository.findAll().getFirst().status());
        }
    }

    /** 适配器迟迟不返回时，Facade 也必须在调用时限内返回 TIMEOUT。 */
    @Test
    void shouldEnforceWallClockTimeout() {
        ModelGateway gateway = gateway(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            throw new TimeoutException("timeout");
        });
        InMemoryModelCallAuditRepository repository = new InMemoryModelCallAuditRepository();

        long started = System.nanoTime();
        try (ModelDiagnosisFacade facade = facade(gateway, repository, Duration.ofMillis(20), 1)) {
            ModelDiagnosisFacade.DiagnosisOutcome outcome = facade.diagnose(snapshot(), "trace-wall-clock");

            assertEquals("TIMEOUT", outcome.status());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 250);
        }
    }

    /** 非法 JSON 应被结构化门禁拒绝，不能按基础设施失败重复调用。 */
    @Test
    void shouldRejectMalformedStructuredOutputWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        ModelGateway gateway = gateway(() -> {
            calls.incrementAndGet();
            return new ModelGateway.ModelCompletion("not-json", "{\"content\":\"not-json\"}");
        });
        InMemoryModelCallAuditRepository repository = new InMemoryModelCallAuditRepository();

        try (ModelDiagnosisFacade facade = facade(gateway, repository, Duration.ofSeconds(1), 2)) {
            ModelDiagnosisFacade.DiagnosisOutcome outcome = facade.diagnose(snapshot(), "trace-gate");

            assertEquals("REJECTED_BY_GATE", outcome.status());
            assertEquals(1, calls.get());
            assertEquals(ModelCallStatus.REJECTED_BY_GATE, repository.findAll().getFirst().status());
            assertTrue(outcome.gateDecision() != null);
        }
    }

    /** 构造一个可用的模型适配器。 */
    private ModelGateway gateway(ThrowingSupplier supplier) {
        return new ModelGateway() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public ModelCompletion complete(ModelRequest request) throws Exception {
                return supplier.get();
            }
        };
    }

    /** 构造测试用 Facade，使用短超时但不依赖真实网络。 */
    private ModelDiagnosisFacade facade(ModelGateway gateway, InMemoryModelCallAuditRepository repository,
                                        Duration timeout, int maxAttempts) {
        return new ModelDiagnosisFacade(gateway, new ModelAuditApplicationService(repository),
                JsonMapper.builder().build(), new org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate(),
                timeout, maxAttempts, Executors.newVirtualThreadPerTaskExecutor(), java.time.Clock.systemUTC());
    }

    /** 构造包含确定性规则证据的可回放诊断快照。 */
    private DiagnosisSnapshot snapshot() {
        return new DiagnosisSnapshot("snapshot-1", Instant.parse("2026-09-13T00:00:00Z"),
                new MetricsEvidence("synthetic-provider", "credit-apply", 100, 800, 0.1, 45, 50),
                List.of(new RuleFinding("R001", true, "HIGH", "QPS 超过契约", "降低调用并发", true)),
                new ContractEvidence("synthetic-provider", "credit-apply", 100, 1000), List.of(), "fingerprint-1");
    }

    /** 构造与快照规则一致的合法模型报告。 */
    private ModelGateway.ModelCompletion validCompletion() throws Exception {
        ModelDiagnosisReport report = new ModelDiagnosisReport("发现接口流量超过契约上限", "HIGH",
                List.of(new ModelFinding("R001", true, "当前 QPS 超过契约上限", "降低调用并发", true)), true);
        String content = JsonMapper.builder().build().writeValueAsString(report);
        return new ModelGateway.ModelCompletion(content, "{\"choices\":[{\"message\":{\"content\":"
                + JsonMapper.builder().build().writeValueAsString(content) + "}}]}");
    }

    /** 允许测试供应商声明受检异常。 */
    @FunctionalInterface
    private interface ThrowingSupplier {
        ModelGateway.ModelCompletion get() throws Exception;
    }
}
