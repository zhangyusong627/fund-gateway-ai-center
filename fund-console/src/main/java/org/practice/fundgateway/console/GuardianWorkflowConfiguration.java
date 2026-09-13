package org.practice.fundgateway.console;

import org.practice.fundgateway.guardian.workflow.DiagnosticTaskRepository;
import org.practice.fundgateway.guardian.workflow.DiagnosticWorkflowService;
import org.practice.fundgateway.guardian.workflow.InMemoryDiagnosticTaskRepository;
import org.practice.fundgateway.guardian.workflow.JdbcDiagnosticTaskRepository;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;
import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配控制台使用的智能守护诊断工作流。 */
@Configuration
public class GuardianWorkflowConfiguration {

    /** 创建无状态的 guardian 诊断评测器，供控制台应用服务复用。 */
    @Bean
    public DiagnosticEvaluationService diagnosticEvaluationService() {
        return new DiagnosticEvaluationService();
    }

    /** 创建诊断任务内存仓储，后续由 PostgreSQL 实现替换。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "memory")
    public DiagnosticTaskRepository diagnosticTaskRepository() {
        return new InMemoryDiagnosticTaskRepository();
    }

    /** 正式模式使用 PostgreSQL 保存诊断、审批和模拟治理事实。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public DiagnosticTaskRepository jdbcDiagnosticTaskRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDiagnosticTaskRepository(jdbcTemplate);
    }

    /** 保存风险指纹，满足诊断工作流主表的外键约束。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public GuardianRiskRepository guardianRiskRepository(JdbcTemplate jdbcTemplate) {
        return new GuardianRiskRepository(jdbcTemplate);
    }

    /** 创建诊断、审批和模拟治理应用服务。 */
    @Bean
    public DiagnosticWorkflowService diagnosticWorkflowService(DiagnosticTaskRepository repository) {
        return new DiagnosticWorkflowService(repository);
    }

}
