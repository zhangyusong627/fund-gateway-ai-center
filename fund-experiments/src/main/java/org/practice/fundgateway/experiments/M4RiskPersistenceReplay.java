package org.practice.fundgateway.experiments;

import java.time.Instant;

import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregate;
import org.practice.fundgateway.guardian.metrics.RiskDiagnosisCoordinator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** M4 PostgreSQL 模拟：验证风险事件和诊断任务的真实幂等落库。 */
@Component
@Profile("m4-persistence")
public class M4RiskPersistenceReplay implements CommandLineRunner {

    /** 执行两次相同风险窗口模拟并打印数据库计数。 */
    @Override
    public void run(String... args) throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
        dataSource.setUser("fund_demo");
        dataSource.setPassword("fund_demo_password");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        GuardianRiskRepository repository = new GuardianRiskRepository(jdbcTemplate);
        repository.ensureSchema();
        RiskDiagnosisCoordinator coordinator = new RiskDiagnosisCoordinator(repository);
        MetricWindowAggregate aggregate = new MetricWindowAggregate("fund-gateway", "/credit/apply",
                Instant.parse("2026-09-20T00:00:00Z"), Instant.parse("2026-09-20T00:00:10Z"),
                100, 40, 10, 48, 50, 3, 180, 10, 600, 0.40, 0.10, 800, 1200);
        RiskDiagnosisCoordinator.CoordinationResult first = coordinator.process(aggregate);
        RiskDiagnosisCoordinator.CoordinationResult second = coordinator.process(aggregate);
        Integer eventCount = jdbcTemplate.queryForObject(
                "select count(*) from guardian.risk_events where service_name=? and interface_path=?",
                Integer.class, "fund-gateway", "/credit/apply");
        Integer taskCount = jdbcTemplate.queryForObject(
                "select count(*) from guardian.diagnostic_tasks where risk_fingerprint=?",
                Integer.class, first.fingerprint());
        System.out.println("M4 PostgreSQL 模拟完成，firstTask=" + first.taskCreated()
                + "，secondTask=" + second.taskCreated() + "，riskEvents=" + eventCount
                + "，diagnosticTasks=" + taskCount);
    }
}
