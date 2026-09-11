package org.practice.fundgateway.guardian.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证模型报告的结构门禁和规则冲突转人工逻辑。 */
class ModelDiagnosisGateTest {

    /** 规则与模型结论冲突时必须转人工。 */
    @Test
    void shouldRouteConflictToHumanReview() {
        ModelDiagnosisReport report = new ModelDiagnosisReport("发现接口延迟升高", "HIGH",
                List.of(new ModelFinding("R001", false, "引用契约上限", "继续观察接口流量", false)), false);
        DiagnosisSnapshot snapshot = new DiagnosisSnapshot("s-1", Instant.now(), null,
                List.of(new RuleFinding("R001", true, "HIGH", "QPS 超过阈值", "执行限流", true)), null, List.of(), "fp-1");

        ModelDiagnosisGate.GateDecision decision = new ModelDiagnosisGate().assess(report, snapshot);

        assertEquals(ModelDiagnosisGate.GateStatus.HUMAN_REVIEW, decision.status());
    }

    /** 未知规则必须在结构门禁阶段拒绝。 */
    @Test
    void shouldRejectUnknownRule() {
        ModelDiagnosisReport report = new ModelDiagnosisReport("发现风险", "HIGH",
                List.of(new ModelFinding("R999", true, "证据", "建议人工核查", true)), true);

        assertThrows(IllegalArgumentException.class, () -> new ModelDiagnosisGate().validate(report));
    }
}
