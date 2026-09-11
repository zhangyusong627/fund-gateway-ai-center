package org.practice.fundgateway.guardian.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证指标窗口聚合和核心百分位特征计算。 */
class MetricWindowAggregatorTest {

    /** 10 秒窗口应正确累计计数、速率和延迟百分位。 */
    @Test
    void shouldAggregateWindowFeatures() {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        Instant eventTime = Instant.parse("2026-09-20T00:00:03Z");
        aggregator.accept(new MetricEvent("e-001", eventTime, "gateway", "/credit/apply",
                10, 2, 1, 8, 20, 1, 5, List.of(10L, 20L, 30L, 40L, 50L)));

        MetricWindowAggregate result = aggregator.snapshot("gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z"));

        assertEquals(10, result.requestCount());
        assertEquals(0.2, result.errorRate());
        assertEquals(50, result.p95LatencyMs());
        assertEquals(50, result.p99LatencyMs());
    }
}
