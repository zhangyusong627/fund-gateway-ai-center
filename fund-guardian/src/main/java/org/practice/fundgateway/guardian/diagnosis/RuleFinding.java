package org.practice.fundgateway.guardian.diagnosis;

/** 一条确定性规则的评估结果。 */
public record RuleFinding(String ruleId, boolean matched, String riskLevel, String evidence,
                          String recommendation, boolean requiresHumanReview) {
}
