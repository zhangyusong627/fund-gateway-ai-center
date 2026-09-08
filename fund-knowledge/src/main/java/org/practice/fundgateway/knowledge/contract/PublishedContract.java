package org.practice.fundgateway.knowledge.contract;

import java.time.Instant;

/** 表示人工确认后发布的不可变资方契约事实。 */
public record PublishedContract(
        String providerId,
        String interfaceId,
        String source,
        String version,
        Instant effectiveAt,
        int qpsLimit,
        int timeoutMs,
        boolean idempotencySupported) {
}
