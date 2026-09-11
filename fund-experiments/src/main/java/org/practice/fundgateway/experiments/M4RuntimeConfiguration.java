package org.practice.fundgateway.experiments;

import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;
import org.practice.fundgateway.guardian.metrics.MetricEventConsumer;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregator;
import org.practice.fundgateway.guardian.metrics.RiskDiagnosisCoordinator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

/** M4 运行 profile 的 PostgreSQL 风险编排装配。 */
@Configuration
@Profile("m4-runtime")
public class M4RuntimeConfiguration {

    /** 创建本地 PostgreSQL JDBC 模板。 */
    @Bean
    public JdbcTemplate guardianJdbcTemplate() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
        dataSource.setUser("fund_demo");
        dataSource.setPassword("fund_demo_password");
        return new JdbcTemplate(dataSource);
    }

    /** 创建风险事件和诊断任务仓储，并确保表存在。 */
    @Bean
    public GuardianRiskRepository guardianRiskRepository(JdbcTemplate jdbcTemplate) {
        GuardianRiskRepository repository = new GuardianRiskRepository(jdbcTemplate);
        repository.ensureSchema();
        return repository;
    }

    /** 创建连接规则、冷却和 PostgreSQL 的风险编排器。 */
    @Bean
    public RiskDiagnosisCoordinator riskDiagnosisCoordinator(GuardianRiskRepository repository) {
        return new RiskDiagnosisCoordinator(repository);
    }

    /** 在运行 profile 中将消费者连接到风险编排器。 */
    @Bean
    public MetricEventConsumer metricEventConsumer(MetricWindowAggregator aggregator,
                                                   RiskDiagnosisCoordinator coordinator) {
        return new MetricEventConsumer(JsonMapper.builder().build(), aggregator, coordinator);
    }
}
