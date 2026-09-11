package org.practice.fundgateway.guardian.metrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 根据接口和排序后的规则命中生成稳定风险指纹。 */
public final class RiskFingerprint {

    private RiskFingerprint() {
    }

    /** 生成同一风险组合的固定 SHA-256 指纹。 */
    public static String of(MetricWindowAggregate aggregate, List<RiskRuleHit> hits) {
        String rules = hits.stream().map(RiskRuleHit::ruleId).sorted().reduce((left, right) -> left + "," + right)
                .orElse("");
        String source = aggregate.serviceName() + "|" + aggregate.interfacePath() + "|" + rules;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}
