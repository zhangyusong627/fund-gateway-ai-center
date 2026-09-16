package org.practice.fundgateway.guardian.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 在固定冷却窗口内抑制同一风险指纹的重复诊断任务。 */
public class RiskCooldownGate {

    private final Duration cooldown;
    private final Map<String, Instant> nextAllowedAt = new ConcurrentHashMap<>();

    /** 创建指定时长的冷却门禁。 */
    public RiskCooldownGate(Duration cooldown) {
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("冷却时长必须为正数");
        }
        this.cooldown = cooldown;
    }

    /** 尝试放行风险指纹，放行后开始新的冷却周期。 */
    public boolean tryAcquire(String fingerprint, Instant now) {
        if (fingerprint == null || fingerprint.isBlank() || now == null) {
            throw new IllegalArgumentException("风险指纹和时间不能为空");
        }
        java.util.concurrent.atomic.AtomicBoolean acquired = new java.util.concurrent.atomic.AtomicBoolean();
        nextAllowedAt.compute(fingerprint, (ignored, allowedAt) -> {
            if (allowedAt != null && now.isBefore(allowedAt)) {
                return allowedAt;
            }
            acquired.set(true);
            return now.plus(cooldown);
        });
        return acquired.get();
    }

    /** 返回冷却时长，供持久化实现复用同一策略参数。 */
    public Duration cooldown() {
        return cooldown;
    }
}
