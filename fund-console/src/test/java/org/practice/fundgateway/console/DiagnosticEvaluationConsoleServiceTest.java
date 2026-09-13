package org.practice.fundgateway.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticCaseRequest;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticEvaluationRequest;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticObservationRequest;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;

/** 验证控制台评测服务能够复用 guardian 指标并拒绝不完整输入。 */
class DiagnosticEvaluationConsoleServiceTest {

    private final DiagnosticEvaluationConsoleService service =
            new DiagnosticEvaluationConsoleService(new DiagnosticEvaluationService());

    /** 控制台请求应转换为领域模型并返回四类可复算指标。 */
    @Test
    void shouldEvaluateSubmittedDiagnosticCases() {
        DiagnosticEvaluationRequest request = request();

        var response = service.evaluate(request);

        assertThat(response.totalCases()).isEqualTo(2);
        assertThat(response.conclusionAccuracy()).isEqualTo(0.5);
        assertThat(response.evidenceSupportRate()).isEqualTo(1.0);
        assertThat(response.evidenceStopRate()).isEqualTo(1.0);
        assertThat(response.humanReviewRate()).isEqualTo(0.5);
    }

    /** 缺少样例或观测时应阻止生成看似有效的指标。 */
    @Test
    void shouldRejectIncompleteRequest() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.evaluate(new DiagnosticEvaluationRequest(List.of(), List.of())))
                .withMessage("诊断评测样例和观测结果不能为空");
    }

    /** 构造与 guardian 固定单测一致的合成评测输入。 */
    private DiagnosticEvaluationRequest request() {
        return new DiagnosticEvaluationRequest(
                List.of(
                        new DiagnosticCaseRequest("c-1", "synthetic-provider", "credit-apply",
                                List.of("超时"), "上游超时", List.of("metrics", "incident")),
                        new DiagnosticCaseRequest("c-2", "synthetic-provider", "credit-apply",
                                List.of("证据缺失"), "", List.of())),
                List.of(
                        new DiagnosticObservationRequest("c-1", "确认上游超时",
                                List.of("metrics", "incident"), false, GateStatus.ACCEPTED),
                        new DiagnosticObservationRequest("c-2", "", List.of(), true, GateStatus.HUMAN_REVIEW)));
    }
}
