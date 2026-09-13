package org.practice.fundgateway.console;

import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供智能守护合成诊断评测基线接口。 */
@RestController
@RequestMapping("/api/console/guardian")
public class DiagnosticEvaluationController {

    private final DiagnosticEvaluationConsoleService evaluationService;

    /** 注入控制台评测服务。 */
    public DiagnosticEvaluationController(DiagnosticEvaluationConsoleService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 返回不调用模型、不写数据库的固定诊断评测结果。 */
    @GetMapping("/diagnostic-evaluation")
    public DiagnosticEvaluationService.EvaluationSummary evaluate() {
        return evaluationService.evaluateBaseline();
    }
}
