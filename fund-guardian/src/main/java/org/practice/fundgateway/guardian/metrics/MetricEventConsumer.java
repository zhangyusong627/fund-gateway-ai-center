package org.practice.fundgateway.guardian.metrics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.kafka.annotation.KafkaListener;

import tools.jackson.databind.json.JsonMapper;

/** 接收 Redpanda Kafka 协议指标消息，完成 JSON 解析、幂等和窗口聚合。 */
public class MetricEventConsumer {

    private final JsonMapper mapper;
    private final MetricWindowAggregator aggregator;
    private final RiskDiagnosisCoordinator coordinator;
    private final Set<String> consumedEventIds = ConcurrentHashMap.newKeySet();

    /** 创建使用指定窗口聚合器的指标消费者。 */
    public MetricEventConsumer(MetricWindowAggregator aggregator) {
        this(JsonMapper.builder().build(), aggregator, null);
    }

    /** 注入 JSON 映射器和聚合器，便于测试。 */
    public MetricEventConsumer(JsonMapper mapper, MetricWindowAggregator aggregator) {
        this(mapper, aggregator, null);
    }

    /** 注入风险编排器，使消费成功后可以触发确定性诊断任务。 */
    public MetricEventConsumer(JsonMapper mapper, MetricWindowAggregator aggregator,
                               RiskDiagnosisCoordinator coordinator) {
        this.mapper = mapper;
        this.aggregator = aggregator;
        this.coordinator = coordinator;
    }

    /** 监听指标主题；默认关闭自动启动，待本地 Redpanda 配置后启用。 */
    @KafkaListener(topics = "guardian.metric-events.v1", autoStartup = "${guardian.metric-consumer.auto-start:false}")
    public void consume(String payload) throws Exception {
        MetricEvent event = mapper.readValue(payload, MetricEvent.class);
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("指标事件 eventId 不能为空");
        }
        if (consumedEventIds.add(event.eventId())) {
            aggregator.accept(event);
            if (coordinator != null) {
                coordinator.process(snapshot(event));
            }
        }
    }

    /** 根据事件时间读取其所在窗口，作为风险规则输入。 */
    private MetricWindowAggregate snapshot(MetricEvent event) {
        long seconds = 10;
        long epochSecond = event.occurredAt().getEpochSecond();
        java.time.Instant start = java.time.Instant.ofEpochSecond(
                epochSecond - Math.floorMod(epochSecond, seconds));
        return aggregator.snapshot(event.serviceName(), event.interfacePath(), start);
    }

    /** 返回当前已消费的唯一事件数量，用于验证幂等效果。 */
    public int consumedEventCount() {
        return consumedEventIds.size();
    }
}
