package org.practice.fundgateway.console;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;

/** 验证控制台能够调用诊断评测服务并返回固定合成基线。 */
class DiagnosticEvaluationConsoleServiceTest {

    private final DiagnosticEvaluationConsoleService service = new DiagnosticEvaluationConsoleService(
            new DiagnosticEvaluationService());

    /** 固定基线应同时覆盖结论、证据、缺证停止和人工转审指标。 */
    @Test
    void evaluatesSyntheticDiagnosticBaseline() {
        DiagnosticEvaluationService.EvaluationSummary summary = service.evaluateBaseline();

        assertThat(summary.totalCases()).isEqualTo(2);
        assertThat(summary.conclusionHits()).isEqualTo(1);
        assertThat(summary.evidenceHits()).isEqualTo(2);
        assertThat(summary.evidenceStopHits()).isEqualTo(1);
        assertThat(summary.humanReviews()).isEqualTo(1);
        assertThat(summary.conclusionAccuracy()).isEqualTo(0.5);
        assertThat(summary.evidenceSupportRate()).isEqualTo(1.0);
        assertThat(summary.evidenceStopRate()).isEqualTo(1.0);
        assertThat(summary.humanReviewRate()).isEqualTo(0.5);
    }
}
