package org.practice.fundgateway.integration.contract;

import java.time.Instant;

/** 记录人工确认后发布不可变契约版本的审计事件。 */
public record ContractPublishedEvent(
        String eventId,
        String candidateId,
        String providerId,
        String interfaceId,
        String version,
        String reviewer,
        Instant occurredAt) {
}
