package org.practice.fundgateway.guardian.metrics;

import java.time.Instant;

/** 表示一个时间窗口内可复算的运行特征。 */
public record MetricWindowAggregate(
        String serviceName,
        String interfacePath,
        Instant windowStart,
        Instant windowEnd,
        long requestCount,
        long errorCount,
        long timeoutCount,
        long activeThreads,
        long maxThreads,
        long gcCount,
        long gcPauseMs,
        double qps,
        double cpm,
        double errorRate,
        double timeoutRate,
        long p95LatencyMs,
        long p99LatencyMs) {
}
