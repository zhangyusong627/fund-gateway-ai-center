package org.practice.fundgateway.guardian.metrics;

import java.util.List;

/** 表示确定性规则命中的风险事实。 */
public record RiskRuleHit(String ruleId, String severity, List<String> evidence) {

    /** 固定规则证据集合，保证结果可复现。 */
    public RiskRuleHit {
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
