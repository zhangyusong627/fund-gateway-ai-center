package org.practice.fundgateway.console;

import org.practice.fundgateway.guardian.audit.JdbcModelCallAuditRepository;
import org.practice.fundgateway.guardian.audit.InMemoryModelCallAuditRepository;
import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.practice.fundgateway.guardian.audit.ModelCallAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** 装配模型调用审计和成本查询能力。 */
@Configuration
public class AuditConsoleConfiguration {

    /** 正式模式把模型调用审计写入 guardian Schema。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public ModelCallAuditRepository jdbcModelCallAuditRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcModelCallAuditRepository(jdbcTemplate);
    }

    /** 内存模式为本地页面和单元测试提供线程安全替身。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "memory")
    public ModelCallAuditRepository inMemoryModelCallAuditRepository() {
        return new InMemoryModelCallAuditRepository();
    }

    /** 创建模型调用审计应用服务。 */
    @Bean
    public ModelAuditApplicationService modelAuditApplicationService(ModelCallAuditRepository repository) {
        return new ModelAuditApplicationService(repository);
    }
}
