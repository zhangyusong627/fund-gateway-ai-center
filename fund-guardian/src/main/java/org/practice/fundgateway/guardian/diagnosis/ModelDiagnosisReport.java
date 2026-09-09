package org.practice.fundgateway.guardian.diagnosis;

import java.util.List;

/** 模型最终诊断报告的固定结构。 */
public record ModelDiagnosisReport(String summary, String riskLevel,
                                   List<ModelFinding> findings, boolean requiresHumanReview) {
}
