package org.practice.fundgateway.experiments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.practice.fundgateway.integration.contract.ContractReviewController;
import org.practice.fundgateway.integration.contract.ContractReviewService;
import org.practice.fundgateway.guardian.metrics.MetricProcessingConfiguration;

@SpringBootApplication
@Import({ContractReviewController.class, ContractReviewService.class, MetricProcessingConfiguration.class})
/**
 * 资金网关智能决策中心的 Spring Boot 启动入口。
 *
 **/
public class FundGatewayExperimentsApplication {

    /**
     * 启动单进程应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(FundGatewayExperimentsApplication.class, args);
    }

}
