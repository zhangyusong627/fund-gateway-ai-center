package org.practice.fundgateway.guardian.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** 保存模型调用证据、调用时价格快照和可复算成本。 */
public record ModelCallAudit(
        String callId,
        String traceId,
        String domain,
        String stage,
        String provider,
        String model,
        String promptVersion,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long latencyMs,
        ModelCallStatus status,
        int retryCount,
        String priceVersion,
        BigDecimal inputPricePerMillion,
        BigDecimal outputPricePerMillion,
        BigDecimal estimatedCost,
        String currency,
        String rawRequest,
        String rawResponse,
        Instant calledAt) {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    /** 校验模型调用审计字段及成本快照的一致性。 */
    public ModelCallAudit {
        if (isBlank(callId) || isBlank(traceId) || isBlank(domain) || isBlank(stage)
                || isBlank(provider) || isBlank(model) || isBlank(promptVersion) || status == null
                || isBlank(priceVersion) || inputPricePerMillion == null || outputPricePerMillion == null
                || estimatedCost == null || isBlank(currency) || calledAt == null) {
            throw new IllegalArgumentException("模型调用审计缺少必要字段");
        }
        if (inputTokens < 0 || outputTokens < 0 || totalTokens != inputTokens + outputTokens
                || latencyMs < 0 || retryCount < 0 || inputPricePerMillion.signum() < 0
                || outputPricePerMillion.signum() < 0 || estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("模型调用审计的计数或金额无效");
        }
        rawRequest = rawRequest == null ? "" : rawRequest;
        rawResponse = rawResponse == null ? "" : rawResponse;
    }

    /** 使用调用时价格版本计算并创建审计记录。 */
    public static ModelCallAudit priced(String callId, String traceId, String domain, String stage,
                                        String provider, String model, String promptVersion,
                                        long inputTokens, long outputTokens, long latencyMs,
                                        ModelCallStatus status, int retryCount, String priceVersion,
                                        BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion,
                                        String currency, String rawRequest, String rawResponse, Instant calledAt) {
        BigDecimal inputCost = inputPricePerMillion.multiply(BigDecimal.valueOf(inputTokens))
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
        BigDecimal outputCost = outputPricePerMillion.multiply(BigDecimal.valueOf(outputTokens))
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
        return new ModelCallAudit(callId, traceId, domain, stage, provider, model, promptVersion,
                inputTokens, outputTokens, inputTokens + outputTokens, latencyMs, status, retryCount,
                priceVersion, inputPricePerMillion, outputPricePerMillion, inputCost.add(outputCost),
                currency, rawRequest, rawResponse, calledAt);
    }

    /** 判断字符串是否缺少有效内容。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
