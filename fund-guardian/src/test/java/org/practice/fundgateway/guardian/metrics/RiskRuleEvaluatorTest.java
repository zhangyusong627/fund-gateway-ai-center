package org.practice.fundgateway.guardian.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证单指标、多指标规则、指纹和冷却行为。 */
class RiskRuleEvaluatorTest {

    /** 正常窗口不应命中任何风险规则。 */
    @Test
    void shouldNotTriggerForNormalWindow() {
        assertTrue(new RiskRuleEvaluator().evaluate(aggregate(0.01, 100)).isEmpty());
    }

    /** 错误率和延迟同时异常时应命中组合规则。 */
    @Test
    void shouldTriggerSingleAndCombinationRules() {
        List<RiskRuleHit> hits = new RiskRuleEvaluator().evaluate(aggregate(0.40, 800));
        assertEquals(3, hits.size());
        assertTrue(hits.stream().anyMatch(hit -> hit.ruleId().equals("ERROR_AND_LATENCY_COMBINATION")));
        String fingerprint = RiskFingerprint.of(aggregate(0.40, 800), hits);
        RiskCooldownGate gate = new RiskCooldownGate(Duration.ofMinutes(1));
        Instant now = Instant.parse("2026-09-20T00:00:00Z");
        assertTrue(gate.tryAcquire(fingerprint, now));
        assertFalse(gate.tryAcquire(fingerprint, now.plusSeconds(30)));
        assertTrue(gate.tryAcquire(fingerprint, now.plusSeconds(60)));
    }

    /** 创建测试用窗口聚合结果。 */
    private MetricWindowAggregate aggregate(double errorRate, long p95) {
        return new MetricWindowAggregate("gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z"), Instant.parse("2026-09-20T00:00:10Z"),
                100, (long) (errorRate * 100), 0, 20, 50, 0, 0,
                10, 600, errorRate, 0, p95, p95);
    }
}
