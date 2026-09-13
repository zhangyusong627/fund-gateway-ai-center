package org.practice.fundgateway.guardian.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 验证指标 JSON 消费和重复事件幂等处理。 */
class MetricEventConsumerTest {

    private static final String VALID_PAYLOAD = "{\"eventId\":\"evt-1\",\"occurredAt\":\"2026-09-20T00:00:03Z\","
            + "\"serviceName\":\"gateway\",\"interfacePath\":\"/credit/apply\","
            + "\"requestCount\":5,\"errorCount\":1,\"timeoutCount\":0,\"activeThreads\":2,"
            + "\"maxThreads\":10,\"gcCount\":0,\"gcPauseMs\":0,\"latencySamplesMs\":[20]}";

    /** 相同 eventId 重复投递时只进入聚合一次。 */
    @Test
    void shouldConsumeMetricEventIdempotently() throws Exception {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        MetricEventConsumer consumer = new MetricEventConsumer(aggregator);

        consumer.consume(VALID_PAYLOAD);
        consumer.consume(VALID_PAYLOAD);

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

    /** 非法 JSON 只记录不可重放失败，不应污染窗口或触发 Kafka 重试。 */
    @Test
    void shouldRecordInvalidPayloadWithoutThrowing() {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        InMemoryMetricEventFailureRecorder recorder = new InMemoryMetricEventFailureRecorder();
        MetricEventConsumer consumer = new MetricEventConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), aggregator, null, recorder);

        consumer.consume("{not-json");

        assertEquals(0, consumer.consumedEventCount());
        assertEquals(1, recorder.findAll().size());
        MetricEventFailure failure = recorder.findAll().getFirst();
        assertEquals(MetricEventFailure.FailureType.INVALID_MESSAGE, failure.failureType());
        assertEquals(false, failure.replayable());
    }

    /** 非法计数不能进入聚合，避免错误数据制造风险。 */
    @Test
    void shouldRejectInvalidMetricValues() {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        InMemoryMetricEventFailureRecorder recorder = new InMemoryMetricEventFailureRecorder();
        MetricEventConsumer consumer = new MetricEventConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), aggregator, null, recorder);

        consumer.consume(VALID_PAYLOAD.replace("\"errorCount\":1", "\"errorCount\":6"));

        assertEquals(0, consumer.consumedEventCount());
        assertEquals(MetricEventFailure.FailureType.INVALID_MESSAGE, recorder.findAll().getFirst().failureType());
        assertEquals(null, aggregator.snapshot("gateway", "/credit/apply", Instant.parse("2026-09-20T00:00:00Z")));
    }

    /** 同一 eventId 对应不同内容时记录冲突，不能把它当作正常重复消息。 */
    @Test
    void shouldRecordConflictingEventIdAsInvalid() {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        InMemoryMetricEventFailureRecorder recorder = new InMemoryMetricEventFailureRecorder();
        MetricEventConsumer consumer = new MetricEventConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), aggregator, null, recorder);

        consumer.consume(VALID_PAYLOAD);
        consumer.consume(VALID_PAYLOAD.replace("\"requestCount\":5", "\"requestCount\":4"));

        assertEquals(1, consumer.consumedEventCount());
        assertEquals(5, aggregator.snapshot("gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z")).requestCount());
        assertEquals(MetricEventFailure.FailureType.INVALID_MESSAGE, recorder.findAll().getFirst().failureType());
    }

    /** 编排失败重投时只重试编排，不重复累计已经接收的窗口事件。 */
    @Test
    void shouldRetryProcessingFailureWithoutDoubleCounting() throws Exception {
        MetricWindowAggregator aggregator = new MetricWindowAggregator(Duration.ofSeconds(10));
        RiskDiagnosisCoordinator coordinator = Mockito.mock(RiskDiagnosisCoordinator.class);
        InMemoryMetricEventFailureRecorder recorder = new InMemoryMetricEventFailureRecorder();
        when(coordinator.process(any(MetricWindowAggregate.class)))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(new RiskDiagnosisCoordinator.CoordinationResult(false, false, null, java.util.List.of()));
        MetricEventConsumer consumer = new MetricEventConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), aggregator, coordinator, recorder);

        assertThrows(MetricEventProcessingException.class, () -> consumer.consume(VALID_PAYLOAD));
        MetricEventFailure failure = recorder.findAll().getFirst();
        assertEquals(MetricEventFailure.FailureType.PROCESSING_FAILURE, failure.failureType());
        assertEquals(true, failure.replayable());

        consumer.replay(failure.failureId());

        assertEquals(1, consumer.consumedEventCount());
        assertEquals(5, aggregator.snapshot("gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z")).requestCount());
        verify(coordinator, org.mockito.Mockito.times(2)).process(any(MetricWindowAggregate.class));
    }

    /** 非法消息失败记录禁止直接重放，必须修正后重新投递。 */
    @Test
    void shouldNotReplayInvalidFailure() {
        MetricEventConsumer consumer = new MetricEventConsumer(new MetricWindowAggregator(Duration.ofSeconds(10)));

        consumer.consume("{not-json");
        String failureId = consumer.failures().getFirst().failureId();

        assertThrows(IllegalArgumentException.class, () -> consumer.replay(failureId));
    }
}
