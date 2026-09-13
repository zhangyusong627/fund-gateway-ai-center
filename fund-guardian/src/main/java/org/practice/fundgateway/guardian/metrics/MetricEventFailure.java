package org.practice.fundgateway.guardian.metrics;

import java.time.Instant;

/** 记录指标消息失败及其是否允许人工重放。 */
public record MetricEventFailure(
        String failureId,
        FailureType failureType,
        String eventId,
        String payload,
        String reason,
        Instant failedAt,
        boolean replayable) {

    /** 指标消息失败分类；非法消息不能通过重放解决。 */
    public enum FailureType {
        INVALID_MESSAGE,
        PROCESSING_FAILURE
    }
}
