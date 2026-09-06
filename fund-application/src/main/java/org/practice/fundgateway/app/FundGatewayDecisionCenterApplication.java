package org.practice.fundgateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/**
 * 资金网关智能决策中心的 Spring Boot 启动入口。
 *
 **/
public class FundGatewayDecisionCenterApplication {

    /**
     * 启动单进程应用。
     */
    public static void main(String[] args) {
		SpringApplication.run(FundGatewayDecisionCenterApplication.class, args);
    }

}
