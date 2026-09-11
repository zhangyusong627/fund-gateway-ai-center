package org.practice.fundgateway.experiments;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.sql.Timestamp;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;
import org.practice.fundgateway.guardian.metrics.MetricEvent;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregate;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregator;
import org.practice.fundgateway.guardian.metrics.RiskDiagnosisCoordinator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** 从 Redpanda 读取完整指标批次并生成 M4 降频验收统计。 */
@Component
@Profile("m4-acceptance")
public class M4FullReplayAcceptance implements CommandLineRunner {

    private static final String TOPIC = "guardian.metric-events.v1";

    /** 消费 20 条消息，聚合并查询风险事件和诊断任务数量。 */
    @Override
    public void run(String... args) throws Exception {
        List<MetricEvent> events = consumeTwenty();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
        dataSource.setUser("fund_demo");
        dataSource.setPassword("fund_demo_password");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        GuardianRiskRepository repository = new GuardianRiskRepository(jdbcTemplate);
        RiskDiagnosisCoordinator coordinator = new RiskDiagnosisCoordinator(repository);
        RiskDiagnosisCoordinator.CoordinationResult result = null;
        for (MetricEvent event : events) {
            RiskDiagnosisCoordinator.CoordinationResult current = coordinator.process(toAggregate(event));
            if (current.fingerprint() != null) {
                result = current;
            }
        }
        if (result == null) {
            throw new IllegalStateException("完整回放没有识别出风险");
        }
        MetricEvent first = events.get(0);
        Integer riskEvents = jdbcTemplate.queryForObject(
                "select count(*) from guardian.risk_events where service_name=? and interface_path=?",
                Integer.class, "fund-gateway-m4-acceptance", first.interfacePath());
        Integer diagnosticTasks = jdbcTemplate.queryForObject(
                "select count(*) from guardian.diagnostic_tasks where risk_fingerprint=? and window_start>=?",
                Integer.class, result.fingerprint(), Timestamp.from(first.occurredAt().minusSeconds(60)));
        System.out.println("M4 降频验收：metricMessages=" + events.size() + "，riskEvents=" + riskEvents
                + "，diagnosticTasks=" + diagnosticTasks + "，taskCreated=" + result.taskCreated());
    }

    /** 将单条事件转换为可复算的最小窗口，避免正常流量稀释风险样本。 */
    private MetricWindowAggregate toAggregate(MetricEvent event) {
        Instant windowStart = event.occurredAt().minusSeconds(event.occurredAt().getEpochSecond() % 10);
        return new MetricWindowAggregate("fund-gateway-m4-acceptance", event.interfacePath(), windowStart,
                windowStart.plusSeconds(10), event.requestCount(), event.errorCount(), event.timeoutCount(),
                event.activeThreads(), event.maxThreads(), event.gcCount(), event.gcPauseMs(),
                event.requestCount() / 10.0, event.requestCount() * 6.0,
                event.requestCount() == 0 ? 0.0 : (double) event.errorCount() / event.requestCount(),
                event.requestCount() == 0 ? 0.0 : (double) event.timeoutCount() / event.requestCount(),
                percentile(event.latencySamplesMs(), 0.95), percentile(event.latencySamplesMs(), 0.99));
    }

    /** 计算单条事件延迟样本的百分位。 */
    private long percentile(List<Long> samples, double quantile) {
        List<Long> sorted = samples.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * quantile) - 1));
        return sorted.get(index);
    }

    /** 使用独立消费组从 Topic 起始位置读取 20 条消息。 */
    private List<MetricEvent> consumeTwenty() throws Exception {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "m4-acceptance-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        JsonMapper mapper = JsonMapper.builder().build();
        List<MetricEvent> events = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 20_000;
            while (events.size() < 20 && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    events.add(mapper.readValue(record.value(), MetricEvent.class));
                }
            }
        }
        if (events.size() != 20) {
            throw new IllegalStateException("M4 回放消息数量不是 20，实际=" + events.size());
        }
        return events;
    }
}
