package org.practice.fundgateway.guardian.metrics;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;

import tools.jackson.databind.json.JsonMapper;

/** 接收 Redpanda Kafka 协议指标消息，完成校验、幂等和窗口聚合。 */
public class MetricEventConsumer {

    private static final long WINDOW_SECONDS = 10;
    private static final int MAX_REASON_LENGTH = 512;

    private final JsonMapper mapper;
    private final MetricWindowAggregator aggregator;
    private final RiskDiagnosisCoordinator coordinator;
    private final MetricEventFailureRecorder failureRecorder;
    private final Map<String, EventClaim> eventClaims = new HashMap<>();

    /** 创建使用指定窗口聚合器的指标消费者。 */
    public MetricEventConsumer(MetricWindowAggregator aggregator) {
        this(JsonMapper.builder().build(), aggregator, null, new InMemoryMetricEventFailureRecorder());
    }

    /** 注入 JSON 映射器和聚合器，便于测试。 */
    public MetricEventConsumer(JsonMapper mapper, MetricWindowAggregator aggregator) {
        this(mapper, aggregator, null, new InMemoryMetricEventFailureRecorder());
    }

    /** 注入风险编排器，使消费成功后可以触发确定性诊断任务。 */
    public MetricEventConsumer(JsonMapper mapper, MetricWindowAggregator aggregator,
                               RiskDiagnosisCoordinator coordinator) {
        this(mapper, aggregator, coordinator, new InMemoryMetricEventFailureRecorder());
    }

    /** 注入失败记录器，允许测试和本地回放观察失败边界。 */
    public MetricEventConsumer(JsonMapper mapper, MetricWindowAggregator aggregator,
                               RiskDiagnosisCoordinator coordinator,
                               MetricEventFailureRecorder failureRecorder) {
        this.mapper = mapper;
        this.aggregator = aggregator;
        this.coordinator = coordinator;
        this.failureRecorder = failureRecorder;
    }

    /**
     * 监听指标主题；非法消息记录后确认消费，处理失败记录后抛出异常请求 Kafka 重投。
     * 进程重启后内存幂等状态会丢失，跨重启仍遵循 Kafka 至少一次语义。
     */
    @KafkaListener(topics = "guardian.metric-events.v1", autoStartup = "${guardian.metric-consumer.auto-start:false}")
    public synchronized void consume(String payload) {
        MetricEvent event;
        try {
            event = mapper.readValue(payload, MetricEvent.class);
            validate(event);
        } catch (Exception exception) {
            recordFailure(MetricEventFailure.FailureType.INVALID_MESSAGE, eventIdOf(payload), payload, exception);
            return;
        }

        EventClaim existing = eventClaims.get(event.eventId());
        if (existing != null) {
            if (!existing.event().equals(event)) {
                recordFailure(MetricEventFailure.FailureType.INVALID_MESSAGE, event.eventId(), payload,
                        new IllegalArgumentException("同一 eventId 对应了不同消息内容"));
            } else if (existing.state() == ProcessingState.ACCEPTED && coordinator != null) {
                try {
                    processCoordinator(event);
                    eventClaims.put(event.eventId(), new EventClaim(event, ProcessingState.PROCESSED));
                } catch (Exception exception) {
                    MetricEventFailure failure = recordFailure(MetricEventFailure.FailureType.PROCESSING_FAILURE,
                            event.eventId(), payload, exception);
                    throw new MetricEventProcessingException(
                            "指标事件处理失败，等待 Kafka 重投: " + failure.failureId(), exception);
                }
            }
            return;
        }

        boolean acceptedByAggregator = false;
        try {
            aggregator.accept(event);
            acceptedByAggregator = true;
            eventClaims.put(event.eventId(), new EventClaim(event, ProcessingState.ACCEPTED));
            processCoordinator(event);
            eventClaims.put(event.eventId(), new EventClaim(event, ProcessingState.PROCESSED));
        } catch (Exception exception) {
            if (!acceptedByAggregator) {
                eventClaims.remove(event.eventId());
            } else {
                // 聚合已经成功，保留 ACCEPTED；重投只重试风险编排，不重复累计窗口。
                eventClaims.put(event.eventId(), new EventClaim(event, ProcessingState.ACCEPTED));
            }
            MetricEventFailure failure = recordFailure(MetricEventFailure.FailureType.PROCESSING_FAILURE,
                    event.eventId(), payload, exception);
            throw new MetricEventProcessingException("指标事件处理失败，等待 Kafka 重投: " + failure.failureId(), exception);
        }
    }

    /** 仅允许重放处理失败，非法消息必须先修正后以新消息重新投递。 */
    public synchronized void replay(String failureId) {
        MetricEventFailure failure = failureRecorder.find(failureId)
                .orElseThrow(() -> new IllegalArgumentException("失败记录不存在: " + failureId));
        if (!failure.replayable()) {
            throw new IllegalArgumentException("非法消息不可重放，请修正后重新投递");
        }
        consume(failure.payload());
    }

    /** 返回当前进程已接收的唯一事件数量。 */
    public synchronized int consumedEventCount() {
        return eventClaims.size();
    }

    /** 返回失败记录器中的失败证据快照。 */
    public List<MetricEventFailure> failures() {
        return failureRecorder.findAll();
    }

    /** 调用风险编排器；没有编排器时只验证聚合消费链路。 */
    private void processCoordinator(MetricEvent event) throws Exception {
        if (coordinator != null) {
            coordinator.process(snapshot(event));
        }
    }

    /** 校验消息契约，阻止非法计数污染窗口。 */
    private void validate(MetricEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.occurredAt() == null || event.serviceName() == null || event.serviceName().isBlank()
                || event.interfacePath() == null || event.interfacePath().isBlank()) {
            throw new IllegalArgumentException("指标事件缺少必要字段");
        }
        if (event.requestCount() < 0 || event.errorCount() < 0 || event.timeoutCount() < 0
                || event.activeThreads() < 0 || event.maxThreads() < 0
                || event.gcCount() < 0 || event.gcPauseMs() < 0) {
            throw new IllegalArgumentException("指标事件计数不能为负数");
        }
        if (event.errorCount() > event.requestCount() || event.timeoutCount() > event.requestCount()) {
            throw new IllegalArgumentException("错误数或超时数不能超过请求数");
        }
        if (event.activeThreads() > event.maxThreads()) {
            throw new IllegalArgumentException("活跃线程数不能超过最大线程数");
        }
        if (event.latencySamplesMs().stream().anyMatch(sample -> sample == null || sample < 0)) {
            throw new IllegalArgumentException("延迟样本不能为负数或空值");
        }
    }

    /** 根据事件时间读取其所在窗口，作为风险规则输入。 */
    private MetricWindowAggregate snapshot(MetricEvent event) {
        long epochSecond = event.occurredAt().getEpochSecond();
        Instant start = Instant.ofEpochSecond(epochSecond - Math.floorMod(epochSecond, WINDOW_SECONDS));
        return aggregator.snapshot(event.serviceName(), event.interfacePath(), start);
    }

    /** 写入失败证据并返回生成的失败记录。 */
    private MetricEventFailure recordFailure(MetricEventFailure.FailureType type, String eventId,
                                             String payload, Exception exception) {
        MetricEventFailure failure = new MetricEventFailure(UUID.randomUUID().toString(), type, eventId,
                payload == null ? "" : payload, failureReason(exception), Instant.now(),
                type == MetricEventFailure.FailureType.PROCESSING_FAILURE);
        failureRecorder.record(failure);
        return failure;
    }

    /** 从非法原文中尽力提取事件 ID，不为记录失败而再次解析消息。 */
    private String eventIdOf(String payload) {
        try {
            MetricEvent event = mapper.readValue(payload, MetricEvent.class);
            return event == null ? null : event.eventId();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 截断异常原因，避免解析器将过长输入复制到失败记录。 */
    private String failureReason(Exception exception) {
        String reason = exception.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = exception.getClass().getSimpleName();
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    /** 表示同一事件在本进程内的处理阶段。 */
    private record EventClaim(MetricEvent event, ProcessingState state) {
    }

    /** 区分已完成聚合但待重试编排的事件和完整处理事件。 */
    private enum ProcessingState {
        ACCEPTED,
        PROCESSED
    }
}
