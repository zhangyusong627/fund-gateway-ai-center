package org.practice.fundgateway.guardian.diagnosis;

import java.util.List;

/** 规则基线输出的完整诊断结果。 */
public record DiagnosisResult(List<RuleFinding> findings, boolean requiresHumanReview, boolean evidenceSufficient) {
}
