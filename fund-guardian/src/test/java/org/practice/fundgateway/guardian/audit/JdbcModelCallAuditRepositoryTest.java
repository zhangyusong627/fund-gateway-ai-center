package org.practice.fundgateway.guardian.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 验证模型调用成本计算、JDBC 写入和数据库行映射。 */
class JdbcModelCallAuditRepositoryTest {

    /** 调用成本应使用审计记录携带的调用时价格版本计算。 */
    @Test
    void shouldCalculateCostUsingCallTimePriceSnapshot() {
        ModelCallAudit audit = audit();

        assertEquals(0, new BigDecimal("0.0020000000").compareTo(audit.estimatedCost()));
        assertEquals("deepseek-price-2026-09", audit.priceVersion());
        assertEquals(1500, audit.totalTokens());
    }

    /** JDBC 写入应使用包含全部审计字段的固定 SQL。 */
    @Test
    void shouldWriteCompleteAuditUsingJdbcSubstitute() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(eq(JdbcModelCallAuditRepository.INSERT_SQL), any(Object[].class))).thenReturn(1);

        boolean inserted = new JdbcModelCallAuditRepository(jdbcTemplate).saveIfAbsent(audit());

        assertEquals(true, inserted);
        verify(jdbcTemplate).update(eq(JdbcModelCallAuditRepository.INSERT_SQL), any(Object[].class));
    }

    /** 数据库行应恢复 token、重试、价格、原始报文和结果状态。 */
    @Test
    void shouldMapCompleteAuditRow() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Instant calledAt = Instant.parse("2026-09-20T00:00:00Z");
        when(resultSet.getString("call_id")).thenReturn("call-1");
        when(resultSet.getString("trace_id")).thenReturn("trace-1");
        when(resultSet.getString("domain")).thenReturn("guardian");
        when(resultSet.getString("stage")).thenReturn("diagnosis");
        when(resultSet.getString("provider")).thenReturn("deepseek");
        when(resultSet.getString("model")).thenReturn("deepseek-v4-flash");
        when(resultSet.getString("prompt_version")).thenReturn("guardian-diagnosis-v1");
        when(resultSet.getLong("input_tokens")).thenReturn(1000L);
        when(resultSet.getLong("output_tokens")).thenReturn(500L);
        when(resultSet.getLong("total_tokens")).thenReturn(1500L);
        when(resultSet.getLong("latency_ms")).thenReturn(1200L);
        when(resultSet.getString("status")).thenReturn("SUCCEEDED");
        when(resultSet.getInt("retry_count")).thenReturn(1);
        when(resultSet.getString("price_version")).thenReturn("deepseek-price-2026-09");
        when(resultSet.getBigDecimal("input_price_per_million")).thenReturn(new BigDecimal("1.00"));
        when(resultSet.getBigDecimal("output_price_per_million")).thenReturn(new BigDecimal("2.00"));
        when(resultSet.getBigDecimal("estimated_cost")).thenReturn(new BigDecimal("0.0020000000"));
        when(resultSet.getString("currency")).thenReturn("CNY");
        when(resultSet.getString("raw_request")).thenReturn("{\"prompt\":\"诊断\"}");
        when(resultSet.getString("raw_response")).thenReturn("{\"summary\":\"风险\"}");
        when(resultSet.getTimestamp("called_at")).thenReturn(Timestamp.from(calledAt));

        ModelCallAudit mapped = new JdbcModelCallAuditRepository(mock(JdbcTemplate.class))
                .mapRow(resultSet, 0);

        assertEquals(ModelCallStatus.SUCCEEDED, mapped.status());
        assertEquals(1, mapped.retryCount());
        assertEquals("{\"prompt\":\"诊断\"}", mapped.rawRequest());
        assertEquals(calledAt, mapped.calledAt());
    }

    /** 构造固定价格版本下的模型调用审计。 */
    private ModelCallAudit audit() {
        return ModelCallAudit.priced("call-1", "trace-1", "guardian", "diagnosis", "deepseek",
                "deepseek-v4-flash", "guardian-diagnosis-v1", 1000, 500, 1200,
                ModelCallStatus.SUCCEEDED, 1, "deepseek-price-2026-09",
                new BigDecimal("1.00"), new BigDecimal("2.00"), "CNY",
                "{\"prompt\":\"诊断\"}", "{\"summary\":\"风险\"}",
                Instant.parse("2026-09-20T00:00:00Z"));
    }
}
