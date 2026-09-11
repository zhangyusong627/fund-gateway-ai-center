package org.practice.fundgateway.guardian.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/** 使用 PostgreSQL 保存包含价格快照的模型调用审计。 */
public class JdbcModelCallAuditRepository implements ModelCallAuditRepository {

    static final String INSERT_SQL = "insert into guardian.model_call_audits "
            + "(call_id,trace_id,domain,stage,provider,model,prompt_version,input_tokens,output_tokens,total_tokens,"
            + "latency_ms,status,retry_count,price_version,input_price_per_million,output_price_per_million,"
            + "estimated_cost,currency,raw_request,raw_response,called_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + "on conflict (call_id) do nothing";

    private final JdbcTemplate jdbcTemplate;

    /** 注入 JDBC 模板创建模型调用审计仓储。 */
    public JdbcModelCallAuditRepository(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("模型调用审计 JDBC 依赖不能为空");
        }
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 将价格版本、单价和估算成本随调用证据一起幂等写入。 */
    @Override
    public boolean saveIfAbsent(ModelCallAudit audit) {
        int inserted = jdbcTemplate.update(INSERT_SQL, audit.callId(), audit.traceId(), audit.domain(), audit.stage(),
                audit.provider(), audit.model(), audit.promptVersion(), audit.inputTokens(), audit.outputTokens(),
                audit.totalTokens(), audit.latencyMs(), audit.status().name(), audit.retryCount(), audit.priceVersion(),
                audit.inputPricePerMillion(), audit.outputPricePerMillion(), audit.estimatedCost(), audit.currency(),
                audit.rawRequest(), audit.rawResponse(), Timestamp.from(audit.calledAt()));
        return inserted > 0;
    }

    /** 按调用标识读取一条审计记录。 */
    @Override
    public Optional<ModelCallAudit> findByCallId(String callId) {
        return jdbcTemplate.query("select * from guardian.model_call_audits where call_id=?",
                this::mapRow, callId).stream().findFirst();
    }

    /** 按调用时间倒序读取全部审计记录。 */
    @Override
    public List<ModelCallAudit> findAll() {
        return jdbcTemplate.query("select * from guardian.model_call_audits order by called_at desc", this::mapRow);
    }

    /** 将数据库行映射为完整模型调用审计。 */
    ModelCallAudit mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ModelCallAudit(resultSet.getString("call_id"), resultSet.getString("trace_id"),
                resultSet.getString("domain"), resultSet.getString("stage"), resultSet.getString("provider"),
                resultSet.getString("model"), resultSet.getString("prompt_version"),
                resultSet.getLong("input_tokens"), resultSet.getLong("output_tokens"),
                resultSet.getLong("total_tokens"), resultSet.getLong("latency_ms"),
                ModelCallStatus.valueOf(resultSet.getString("status")), resultSet.getInt("retry_count"),
                resultSet.getString("price_version"), resultSet.getBigDecimal("input_price_per_million"),
                resultSet.getBigDecimal("output_price_per_million"), resultSet.getBigDecimal("estimated_cost"),
                resultSet.getString("currency"), resultSet.getString("raw_request"),
                resultSet.getString("raw_response"), resultSet.getTimestamp("called_at").toInstant());
    }
}
