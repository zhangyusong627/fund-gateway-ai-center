package org.practice.fundgateway.console;

import org.practice.fundgateway.guardian.ai.ModelDiagnosisFacade;
import org.practice.fundgateway.guardian.ai.ModelGateway;
import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/** 装配正式控制台唯一的模型适配器和诊断 Facade。 */
@Configuration
public class ModelConsoleConfiguration {

    /** 创建 DeepSeek 出站适配器，密钥只从环境变量读取。 */
    @Bean
    public ModelGateway deepSeekModelGateway() {
        return new DeepSeekModelGateway(System.getenv("DEEPSEEK_API_KEY"), JsonMapper.builder().build());
    }

    /** 创建统一模型治理 Facade，并在 Spring 停止时释放调用执行器。 */
    @Bean(destroyMethod = "close")
    public ModelDiagnosisFacade modelDiagnosisFacade(ModelGateway gateway,
                                                     ModelAuditApplicationService auditService) {
        return new ModelDiagnosisFacade(gateway, auditService);
    }
}
