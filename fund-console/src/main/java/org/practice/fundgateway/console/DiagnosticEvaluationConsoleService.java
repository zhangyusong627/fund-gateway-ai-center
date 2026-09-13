package org.practice.fundgateway.console;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.practice.fundgateway.console.ConsoleModels.DiagnosticCaseRequest;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticEvaluationRequest;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticEvaluationResponse;
import org.practice.fundgateway.console.ConsoleModels.DiagnosticObservationRequest;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticCase;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;

import org.springframework.stereotype.Service;

/** 将控制台请求转换为 guardian 评测模型，并返回无状态的诊断指标。 */
@Service
public class DiagnosticEvaluationConsoleService {

    private final DiagnosticEvaluationService evaluationService;

    /** 注入已有的 guardian 诊断评测服务。 */
    public DiagnosticEvaluationConsoleService(DiagnosticEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 评估调用方提交的固定合成样例，不保存评测结果也不调用模型。 */
    public DiagnosticEvaluationResponse evaluate(DiagnosticEvaluationRequest request) {
        validateRequest(request);
        List<DiagnosticCase> cases = request.cases().stream().map(this::toDomainCase).toList();
        List<DiagnosticEvaluationService.Observation> observations = request.observations().stream()
                .map(this::toDomainObservation).toList();
        return DiagnosticEvaluationResponse.from(evaluationService.evaluate(cases, observations));
    }

    /** 校验控制台边界，避免无效输入进入领域评测器后产生误导指标。 */
    private void validateRequest(DiagnosticEvaluationRequest request) {
        if (request == null || request.cases() == null || request.cases().isEmpty()
                || request.observations() == null || request.observations().isEmpty()) {
            throw new IllegalArgumentException("诊断评测样例和观测结果不能为空");
        }
        Set<String> caseIds = new HashSet<>();
        for (DiagnosticCaseRequest item : request.cases()) {
            if (item == null || isBlank(item.caseId()) || isBlank(item.providerId()) || isBlank(item.interfaceId())
                    || item.symptoms() == null || item.requiredEvidence() == null) {
                throw new IllegalArgumentException("诊断评测样例字段不完整");
            }
            if (!caseIds.add(item.caseId())) {
                throw new IllegalArgumentException("诊断评测样例 caseId 重复：" + item.caseId());
            }
        }
        Set<String> observationIds = new HashSet<>();
        for (DiagnosticObservationRequest item : request.observations()) {
            if (item == null || isBlank(item.caseId()) || item.evidenceIds() == null || item.gateStatus() == null) {
                throw new IllegalArgumentException("诊断观测结果字段不完整");
            }
            if (!observationIds.add(item.caseId())) {
                throw new IllegalArgumentException("诊断观测结果 caseId 重复：" + item.caseId());
            }
        }
    }

    /** 转换控制台评测样例为 guardian 领域样例。 */
    private DiagnosticCase toDomainCase(DiagnosticCaseRequest item) {
        return new DiagnosticCase(item.caseId(), item.providerId(), item.interfaceId(), item.symptoms(),
                item.expectedConclusion(), item.requiredEvidence());
    }

    /** 转换控制台观测结果为 guardian 领域观测。 */
    private DiagnosticEvaluationService.Observation toDomainObservation(DiagnosticObservationRequest item) {
        ModelDiagnosisGate.GateStatus status = item.gateStatus();
        return new DiagnosticEvaluationService.Observation(item.caseId(), item.actualConclusion(), item.evidenceIds(),
                item.stoppedWithoutEvidence(), status);
    }

    /** 判断字符串是否为空。 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
