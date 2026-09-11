package org.practice.fundgateway.guardian.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 验证指标 JSON 消费和重复事件幂等处理。 */
class MetricEventConsumerTest {

    /** 相同 eventId 重复投递时只进入聚合一次。 */
    @Test
    void shouldConsumeMetricEventIdempotently() throws Exception {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        MetricEventConsumer consumer = new MetricEventConsumer(aggregator);
        String payload = "{\"eventId\":\"evt-1\",\"occurredAt\":\"2026-09-20T00:00:03Z\","
                + "\"serviceName\":\"gateway\",\"interfacePath\":\"/credit/apply\","
                + "\"requestCount\":5,\"errorCount\":1,\"timeoutCount\":0,\"activeThreads\":2,"
                + "\"maxThreads\":10,\"gcCount\":0,\"gcPauseMs\":0,\"latencySamplesMs\":[20]}";

        consumer.consume(payload);
        consumer.consume(payload);

        assertEquals(1, consumer.consumedEventCount());
        assertEquals(5, aggregator.snapshot("gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z")).requestCount());
    }

    /** 消费新事件后应把当前窗口交给风险编排器。 */
    @Test
    void shouldTriggerRiskCoordinatorAfterAggregation() throws Exception {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        RiskDiagnosisCoordinator coordinator = Mockito.mock(RiskDiagnosisCoordinator.class);
        MetricEventConsumer consumer = new MetricEventConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), aggregator, coordinator);
        consumer.consume("{\"eventId\":\"evt-2\",\"occurredAt\":\"2026-09-20T00:00:03Z\","
                + "\"serviceName\":\"gateway\",\"interfacePath\":\"/credit/apply\","
                + "\"requestCount\":5,\"errorCount\":1,\"timeoutCount\":0,\"activeThreads\":2,"
                + "\"maxThreads\":10,\"gcCount\":0,\"gcPauseMs\":0,\"latencySamplesMs\":[20]}");
        verify(coordinator).process(any(MetricWindowAggregate.class));
    }
}
