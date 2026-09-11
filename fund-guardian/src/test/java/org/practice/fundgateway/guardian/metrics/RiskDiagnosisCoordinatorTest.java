package org.practice.fundgateway.guardian.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 验证风险编排对正常窗口和冷却重复风险的处理。 */
class RiskDiagnosisCoordinatorTest {

    private final GuardianRiskRepository repository = Mockito.mock(GuardianRiskRepository.class);
    private final RiskDiagnosisCoordinator coordinator = new RiskDiagnosisCoordinator(
            new RiskRuleEvaluator(), new RiskCooldownGate(Duration.ofMinutes(1)), repository,
            tools.jackson.databind.json.JsonMapper.builder().build(),
            Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC));

    /** 正常窗口不写风险事件，也不创建诊断任务。 */
    @Test
    void shouldIgnoreNormalWindow() throws Exception {
        RiskDiagnosisCoordinator.CoordinationResult result = coordinator.process(aggregate(0.01, 100));
        assertFalse(result.riskDetected());
        verify(repository, never()).saveRiskEvent(any());
        verify(repository, never()).saveDiagnosticTask(anyString(), any(), anyString());
    }

    /** 同一风险在冷却期内只创建一个任务。 */
    @Test
    void shouldCreateOnlyOneTaskDuringCooldown() throws Exception {
        when(repository.saveDiagnosticTask(anyString(), any(), anyString())).thenReturn(true);
        assertTrue(coordinator.process(aggregate(0.40, 800)).taskCreated());
        assertFalse(coordinator.process(aggregate(0.40, 800)).taskCreated());
    }

    /** 创建测试窗口聚合。 */
    private MetricWindowAggregate aggregate(double errorRate, long p95) {
        return new MetricWindowAggregate("gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z"), Instant.parse("2026-09-20T00:00:10Z"),
                100, (long) (errorRate * 100), 0, 20, 50, 0, 0,
                10, 600, errorRate, 0, p95, p95);
    }
}
