package org.practice.fundgateway.guardian.metrics;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 提供指标窗口聚合、失败记录和消息消费者的默认装配。 */
@Configuration
public class MetricProcessingConfiguration {

    /** 创建十秒窗口聚合器，后续可通过配置拆分一分钟窗口。 */
    @Bean
    public MetricWindowAggregator metricWindowAggregator() {
        return new MetricWindowAggregator(Duration.ofSeconds(10));
    }

    /** 创建本进程内失败记录器，失败重放不跨进程持久化。 */
    @Bean
    public MetricEventFailureRecorder metricEventFailureRecorder() {
        return new InMemoryMetricEventFailureRecorder();
    }

    /** 创建指标消息消费者。 */
    @Profile("!m4-runtime")
    @Bean
    public MetricEventConsumer metricEventConsumer(MetricWindowAggregator aggregator,
                                                   MetricEventFailureRecorder failureRecorder) {
        return new MetricEventConsumer(tools.jackson.databind.json.JsonMapper.builder().build(), aggregator,
                null, failureRecorder);
    }
}
