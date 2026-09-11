package org.practice.fundgateway.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 启动资方接入助手应用，负责候选规范审核与发布接口。 */
@SpringBootApplication
public class FundIntegrationApplication {

    /** 启动资方接入助手 Spring Boot 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(FundIntegrationApplication.class, args);
    }

    /** 提供无需外部依赖的存活检查接口。 */
    @RestController
    static class HealthController {

        /** 返回应用存活状态。 */
        @GetMapping("/health")
        public String health() {
            return "UP";
        }
    }
}
