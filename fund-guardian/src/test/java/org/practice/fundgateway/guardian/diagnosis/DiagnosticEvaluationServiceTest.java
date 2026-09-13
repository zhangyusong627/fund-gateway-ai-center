package org.practice.fundgateway.guardian.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证诊断评测的结论、证据不足停止和人工转审指标。 */
class DiagnosticEvaluationServiceTest {

    private final DiagnosticEvaluationService service = new DiagnosticEvaluationService();

    /** 固定样例应得到可复算的命中率和人工转审率。 */
    @Test
    void calculatesDiagnosticMetrics() {
        List<DiagnosticCase> cases = List.of(
                new DiagnosticCase("c-1", "synthetic-provider", "credit-apply", List.of("超时"),
                        "上游超时", List.of("metrics", "incident")),
                new DiagnosticCase("c-2", "synthetic-provider", "credit-apply", List.of("证据缺失"),
                        "", List.of()));
        List<DiagnosticEvaluationService.Observation> observations = List.of(
                new DiagnosticEvaluationService.Observation("c-1", "确认上游超时", List.of("metrics", "incident"),
                        false, ModelDiagnosisGate.GateStatus.ACCEPTED),
                new DiagnosticEvaluationService.Observation("c-2", "", List.of(), true,
                        ModelDiagnosisGate.GateStatus.HUMAN_REVIEW));

        DiagnosticEvaluationService.EvaluationSummary summary = service.evaluate(cases, observations);

        assertEquals(2, summary.totalCases());
        assertEquals(1, summary.conclusionHits());
        assertEquals(2, summary.evidenceHits());
        assertEquals(1, summary.evidenceStopHits());
        assertEquals(0.5, summary.conclusionAccuracy());
        assertEquals(1.0, summary.evidenceSupportRate());
        assertEquals(1.0, summary.evidenceStopRate());
        assertEquals(0.5, summary.humanReviewRate());
    }
}
