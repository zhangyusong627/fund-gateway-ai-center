package org.practice.fundgateway.guardian.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    /** 并发写入同一窗口时不应丢失任何指标计数。 */
    @Test
    void shouldAggregateConcurrentEventsWithoutLostUpdates() throws Exception {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int index = 0; index < 1000; index++) {
            int eventIndex = index;
            executor.submit(() -> aggregator.accept(new MetricEvent("e-" + eventIndex,
                    Instant.parse("2026-09-20T00:00:03Z"), "gateway", "/credit/apply",
                    1, 0, 0, 1, 10, 0, 0, List.of(10L))));
        }
        executor.shutdown();
        assertEquals(true, executor.awaitTermination(5, TimeUnit.SECONDS));

        MetricWindowAggregate result = aggregator.snapshot("gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z"));
        assertEquals(1000, result.requestCount());
    }
}
