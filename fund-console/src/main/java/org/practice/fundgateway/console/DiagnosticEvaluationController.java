package org.practice.fundgateway.console;

import org.practice.fundgateway.console.ConsoleModels.DiagnosticEvaluationRequest;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticEvaluationResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供诊断评测入口，供控制台和固定合成评测脚本复用。 */
@RestController
@RequestMapping("/api/console/guardian/evaluation")
public class DiagnosticEvaluationController {

    private final DiagnosticEvaluationConsoleService evaluationService;

    /** 注入控制台诊断评测应用服务。 */
    public DiagnosticEvaluationController(DiagnosticEvaluationConsoleService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 计算结论、证据、缺证停止和人工转审指标。 */
    @PostMapping
    public DiagnosticEvaluationResponse evaluate(@RequestBody DiagnosticEvaluationRequest request) {
        return evaluationService.evaluate(request);
    }
}
