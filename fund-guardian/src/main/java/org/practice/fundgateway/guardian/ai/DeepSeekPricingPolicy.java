package org.practice.fundgateway.guardian.ai;

import java.math.BigDecimal;

/** 保存 DeepSeek 官方价格快照，并按 Token 估算单次调用成本。 */
public final class DeepSeekPricingPolicy {

    /** 价格来源版本；价格单位为美元 / 一百万 Token。 */
    public static final String PRICE_VERSION = "deepseek-api-v4.1-flash-offpeak-2026-09";
    public static final String CURRENCY = "USD";

    private static final BigDecimal CACHE_HIT_PRICE = new BigDecimal("0.003");
    private static final BigDecimal CACHE_MISS_PRICE = new BigDecimal("0.15");
    private static final BigDecimal OUTPUT_PRICE = new BigDecimal("0.6");

    private DeepSeekPricingPolicy() {
    }

    /** 返回当前项目模型对应的价格快照；未知模型不允许静默套用价格。 */
    public static PriceSnapshot snapshot(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (!model.equalsIgnoreCase("deepseek-v4-flash")
                && !model.equalsIgnoreCase("deepseek-v4.1-flash")) {
            throw new IllegalArgumentException("没有预置 DeepSeek 模型价格：" + model);
        }
        return new PriceSnapshot(PRICE_VERSION, CURRENCY, CACHE_HIT_PRICE, CACHE_MISS_PRICE, OUTPUT_PRICE);
    }

    /** 表示一次调用采用的输入缓存和输出价格快照。 */
    public record PriceSnapshot(String version, String currency, BigDecimal inputCacheHitPerMillion,
                                BigDecimal inputCacheMissPerMillion, BigDecimal outputPerMillion) {

        /** 按缓存未命中输入估算，避免缺少缓存 Token 明细时低估成本。 */
        public BigDecimal estimateConservative(long inputTokens, long outputTokens) {
            if (inputTokens < 0 || outputTokens < 0) {
                throw new IllegalArgumentException("Token 数量不能为负数");
            }
            return inputCacheMissPerMillion.multiply(BigDecimal.valueOf(inputTokens))
                    .add(outputPerMillion.multiply(BigDecimal.valueOf(outputTokens)))
                    .divide(BigDecimal.valueOf(1_000_000L), 10, java.math.RoundingMode.HALF_UP);
        }
    }
}
