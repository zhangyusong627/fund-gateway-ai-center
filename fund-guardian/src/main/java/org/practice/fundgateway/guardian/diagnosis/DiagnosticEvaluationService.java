package org.practice.fundgateway.guardian.diagnosis;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 对固定合成诊断集计算可复现的结论、证据和门禁指标。 */
public class DiagnosticEvaluationService {

    /** 评估一批固定样例，不改变样例或运行状态。 */
    public EvaluationSummary evaluate(List<DiagnosticCase> cases, List<Observation> observations) {
        if (cases == null || cases.isEmpty() || observations == null) {
            throw new IllegalArgumentException("诊断评测集和观测结果不能为空");
        }
        Map<String, Observation> byCase = observations.stream()
                .collect(Collectors.toMap(Observation::caseId, Function.identity(), (first, ignored) -> first));
        int conclusionHits = 0;
        int evidenceHits = 0;
        int evidenceStopHits = 0;
        int humanReviews = 0;
        for (DiagnosticCase diagnosticCase : cases) {
            Observation observation = byCase.get(diagnosticCase.caseId());
            if (observation == null) {
                throw new IllegalArgumentException("缺少诊断样例观测结果：" + diagnosticCase.caseId());
            }
            if (containsConclusion(observation.actualConclusion(), diagnosticCase.expectedConclusion())) {
                conclusionHits++;
            }
            if (observation.evidenceIds().containsAll(diagnosticCase.requiredEvidence())) {
                evidenceHits++;
            }
            if (diagnosticCase.requiredEvidence().isEmpty() && observation.stoppedWithoutEvidence()) {
                evidenceStopHits++;
            }
            if (observation.gateStatus() == ModelDiagnosisGate.GateStatus.HUMAN_REVIEW) {
                humanReviews++;
            }
        }
        int total = cases.size();
        return new EvaluationSummary(total, conclusionHits, evidenceHits, evidenceStopHits, humanReviews,
                ratio(conclusionHits, total), ratio(evidenceHits, total),
                ratio(evidenceStopHits, cases.stream().filter(item -> item.requiredEvidence().isEmpty()).count()),
                ratio(humanReviews, total));
    }

    /** 判断模型结论是否明确包含金标准结论。 */
    private boolean containsConclusion(String actual, String expected) {
        return actual != null && expected != null && !expected.isBlank() && actual.contains(expected);
    }

    /** 计算可复算比例，分母为零时返回 0。 */
    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0D : (double) numerator / denominator;
    }

    /** 一道样例的可审计观测结果。 */
    public record Observation(String caseId, String actualConclusion, List<String> evidenceIds,
                              boolean stoppedWithoutEvidence, ModelDiagnosisGate.GateStatus gateStatus) {

        /** 校验观测结果的最小字段，避免评测缺数据却得到高分。 */
        public Observation {
            if (caseId == null || caseId.isBlank() || evidenceIds == null || gateStatus == null) {
                throw new IllegalArgumentException("诊断观测结果字段不完整");
            }
        }
    }

    /** 一次评测的固定汇总结果。 */
    public record EvaluationSummary(int totalCases, int conclusionHits, int evidenceHits, int evidenceStopHits,
                                    int humanReviews, double conclusionAccuracy, double evidenceSupportRate,
                                    double evidenceStopRate, double humanReviewRate) {
    }
}
