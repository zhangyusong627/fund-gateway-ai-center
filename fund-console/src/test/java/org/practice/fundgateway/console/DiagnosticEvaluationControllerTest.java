package org.practice.fundgateway.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticCaseRequest;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticEvaluationRequest;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticObservationRequest;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;

/** 验证控制器暴露的诊断评测入口返回统一指标模型。 */
class DiagnosticEvaluationControllerTest {

    /** POST 入口应调用控制台服务并保留领域计算结果。 */
    @Test
    void shouldExposeEvaluationMetrics() {
        var service = new DiagnosticEvaluationConsoleService(new DiagnosticEvaluationService());
        var controller = new DiagnosticEvaluationController(service);
        var request = new DiagnosticEvaluationRequest(
                List.of(new DiagnosticCaseRequest("c-1", "synthetic-provider", "credit-apply",
                        List.of("超时"), "上游超时", List.of("metrics"))),
                List.of(new DiagnosticObservationRequest("c-1", "确认上游超时", List.of("metrics"),
                        false, GateStatus.ACCEPTED)));

        var response = controller.evaluate(request);

        assertThat(response.totalCases()).isEqualTo(1);
        assertThat(response.conclusionHits()).isEqualTo(1);
        assertThat(response.evidenceHits()).isEqualTo(1);
        assertThat(response.humanReviewRate()).isZero();
    }
}
