package org.practice.fundgateway.guardian.metrics;

import java.util.ArrayList;
import java.util.List;

/** 基于窗口特征执行单指标和多指标风险规则。 */
public class RiskRuleEvaluator {

    /** 评估窗口并返回全部命中的确定性规则。 */
    public List<RiskRuleHit> evaluate(MetricWindowAggregate aggregate) {
        if (aggregate == null) {
            return List.of();
        }
        List<RiskRuleHit> hits = new ArrayList<>();
        if (aggregate.errorRate() >= 0.20) {
            hits.add(new RiskRuleHit("ERROR_RATE_HIGH", "HIGH",
                    List.of("errorRate=" + aggregate.errorRate())));
        }
        if (aggregate.p95LatencyMs() >= 500) {
            hits.add(new RiskRuleHit("P95_LATENCY_HIGH", "MEDIUM",
                    List.of("p95LatencyMs=" + aggregate.p95LatencyMs())));
        }
        if (aggregate.errorRate() >= 0.10 && aggregate.p95LatencyMs() >= 500) {
            hits.add(new RiskRuleHit("ERROR_AND_LATENCY_COMBINATION", "CRITICAL",
                    List.of("errorRate=" + aggregate.errorRate(),
                            "p95LatencyMs=" + aggregate.p95LatencyMs())));
        }
        return List.copyOf(hits);
    }
}
