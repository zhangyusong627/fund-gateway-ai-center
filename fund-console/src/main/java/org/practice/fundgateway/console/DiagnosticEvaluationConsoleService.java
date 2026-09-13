package org.practice.fundgateway.console;

import java.util.List;

import org.practice.fundgateway.guardian.diagnosis.DiagnosticCase;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.springframework.stereotype.Service;

/** 为控制台提供固定合成诊断评测基线，不触发模型调用或数据库写入。 */
@Service
public class DiagnosticEvaluationConsoleService {

    private static final List<DiagnosticCase> BASELINE_CASES = List.of(
            new DiagnosticCase("c-1", "synthetic-provider", "credit-apply", List.of("超时"),
                    "上游超时", List.of("metrics", "incident")),
            new DiagnosticCase("c-2", "synthetic-provider", "credit-apply", List.of("证据缺失"),
                    "", List.of()));

    private static final List<DiagnosticEvaluationService.Observation> BASELINE_OBSERVATIONS = List.of(
            new DiagnosticEvaluationService.Observation("c-1", "确认上游超时", List.of("metrics", "incident"),
                    false, ModelDiagnosisGate.GateStatus.ACCEPTED),
            new DiagnosticEvaluationService.Observation("c-2", "", List.of(), true,
                    ModelDiagnosisGate.GateStatus.HUMAN_REVIEW));

    private final DiagnosticEvaluationService evaluationService;

    /** 注入守护域的确定性评测服务。 */
    public DiagnosticEvaluationConsoleService(DiagnosticEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 计算固定合成诊断基线，结果可由样例和观测结果复算。 */
    public DiagnosticEvaluationService.EvaluationSummary evaluateBaseline() {
        return evaluationService.evaluate(BASELINE_CASES, BASELINE_OBSERVATIONS);
    }
}
