package org.practice.fundgateway.guardian;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 静态验证 guardian SQL 包含第二阶段持久化契约，不执行任何 DDL。 */
class GuardianSchemaContractTest {

    /** SQL 应定义工作流子表和完整模型调用审计字段。 */
    @Test
    void shouldContainWorkflowAndModelAuditContracts() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/guardian-schema.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("guardian.diagnosis_workflow_tasks"));
            assertTrue(sql.contains("guardian.diagnosis_timeline"));
            assertTrue(sql.contains("guardian.diagnosis_approvals"));
            assertTrue(sql.contains("guardian.governance_simulations"));
            assertTrue(sql.contains("guardian.model_call_audits"));
            for (String field : new String[] {"call_id", "trace_id", "domain", "stage", "provider", "model",
                    "prompt_version", "input_tokens", "output_tokens", "total_tokens", "latency_ms", "status",
                    "retry_count", "price_version", "input_price_per_million", "output_price_per_million",
                    "estimated_cost", "currency", "raw_request", "raw_response"}) {
                assertTrue(sql.contains(field), "缺少模型调用审计字段：" + field);
            }
        }
    }
}
