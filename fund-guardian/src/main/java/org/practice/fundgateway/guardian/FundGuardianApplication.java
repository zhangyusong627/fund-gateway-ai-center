package org.practice.fundgateway.guardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 启动智能守护应用，默认只装配指标处理能力，不自动消费消息。 */
@SpringBootApplication
public class FundGuardianApplication {

    /** 启动智能守护 Spring Boot 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(FundGuardianApplication.class, args);
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
