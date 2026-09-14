package org.practice.fundgateway.guardian.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** 验证 DeepSeek 价格快照和保守成本估算规则。 */
class DeepSeekPricingPolicyTest {

    /** V4.1-Flash 使用官方价格快照并按缓存未命中输入估算。 */
    @Test
    void shouldEstimateConservativeCost() {
        DeepSeekPricingPolicy.PriceSnapshot pricing = DeepSeekPricingPolicy.snapshot("deepseek-v4-flash");

        assertEquals(new BigDecimal("0.15"), pricing.inputCacheMissPerMillion());
        assertEquals(new BigDecimal("0.6"), pricing.outputPerMillion());
        assertEquals(new BigDecimal("0.0001500000"), pricing.estimateConservative(1000, 0));
        assertEquals(new BigDecimal("0.6001500000"), pricing.estimateConservative(1000, 1_000_000));
    }

    /** 未知模型不能静默使用错误价格。 */
    @Test
    void shouldRejectUnknownModel() {
        assertThrows(IllegalArgumentException.class,
                () -> DeepSeekPricingPolicy.snapshot("unknown-model"));
    }
}
