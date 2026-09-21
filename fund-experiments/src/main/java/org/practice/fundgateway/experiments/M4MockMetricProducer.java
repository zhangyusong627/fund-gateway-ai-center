package org.practice.fundgateway.experiments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.practice.fundgateway.guardian.metrics.MetricEvent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** 向本地 Redpanda 发送合成指标事件，用于模拟 M4 消费链路。 */
@Component
@Profile("m4-producer")
public class M4MockMetricProducer implements CommandLineRunner {

    private static final String TOPIC = "guardian.metric-events.v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 注入 Kafka 消息模板。 */
    public M4MockMetricProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** 发送正常和风险两组指标事件，并输出模拟数量。 */
    @Override
    public void run(String... args) throws Exception {
        int sent = 0;
        for (int index = 0; index < 18; index++) {
            send(normalEvent(index));
            sent++;
        }
        for (int index = 0; index < 2; index++) {
            send(riskEvent(index));
            sent++;
        }
        System.out.println("M4 Mock 指标发送完成，topic=" + TOPIC + "，消息数=" + sent);
    }

    /** 发送一条指标事件并等待 broker 确认。 */
    private void send(MetricEvent event) throws Exception {
        kafkaTemplate.send(TOPIC, event.interfacePath(), mapper.writeValueAsString(event))
                .get(10, TimeUnit.SECONDS);
    }

    /** 构造正常指标样本。 */
    private MetricEvent normalEvent(int index) {
        return new MetricEvent(UUID.randomUUID().toString(), Instant.now(), "fund-gateway",
                "/credit/apply", 20, 0, 0, 8, 50, 0, 0, List.of(80L, 90L, 100L));
    }

    /** 构造错误率和延迟同时升高的风险样本。 */
    private MetricEvent riskEvent(int index) {
        return new MetricEvent(UUID.randomUUID().toString(), Instant.now(), "fund-gateway",
                "/credit/apply", 20, 8, 5, 48, 50, 3, 180, List.of(800L, 900L, 1200L));
    }
}
