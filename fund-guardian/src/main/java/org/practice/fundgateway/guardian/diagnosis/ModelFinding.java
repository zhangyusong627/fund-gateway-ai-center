package org.practice.fundgateway.guardian.diagnosis;

/** 模型报告中的单条发现。 */
public record ModelFinding(String ruleId, boolean matched, String evidence,
                           String recommendation, boolean requiresHumanReview) {
}
