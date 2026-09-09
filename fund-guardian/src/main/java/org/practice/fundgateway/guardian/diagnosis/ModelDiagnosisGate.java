package org.practice.fundgateway.guardian.diagnosis;

import java.util.Set;

/** 校验模型最终报告的结构、枚举值和规则白名单。 */
public class ModelDiagnosisGate {
    private static final Set<String> RULES = Set.of("R001", "R002", "R003", "R004", "R005");
    private static final Set<String> RISKS = Set.of("LOW", "MEDIUM", "HIGH");

    /** 通过则返回原报告，失败则抛出异常并停止后续流程。 */
    public ModelDiagnosisReport validate(ModelDiagnosisReport report) {
        if (report == null || report.summary() == null || report.summary().isBlank()
                || report.riskLevel() == null || !RISKS.contains(report.riskLevel())
                || report.findings() == null || report.findings().isEmpty()
                || !hasChinese(report.summary())) {
            throw new IllegalArgumentException("模型诊断报告结构不完整");
        }
        for (ModelFinding finding : report.findings()) {
            if (finding == null || !RULES.contains(finding.ruleId())
                    || finding.evidence() == null || finding.recommendation() == null
                    || !hasChinese(finding.evidence()) || !hasChinese(finding.recommendation())) {
                throw new IllegalArgumentException("模型诊断报告包含未知规则或缺失字段");
            }
        }
        return report;
    }

    /** 业务文本至少包含一个中文字符，技术标识仍可保留英文。 */
    private boolean hasChinese(String value) {
        return value != null && value.matches(".*[\\u4E00-\\u9FFF].*");
    }
}
