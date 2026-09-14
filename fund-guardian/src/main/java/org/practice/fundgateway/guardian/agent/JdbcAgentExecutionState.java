package org.practice.fundgateway.guardian.agent;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** 使用 PostgreSQL 保存 Agent 执行游标，支持进程中断后的状态恢复。 */
public class JdbcAgentExecutionState implements AgentExecutionStatePort {
    private final JdbcTemplate jdbcTemplate;

    /** 注入数据库访问模板。 */
    public JdbcAgentExecutionState(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    /** 按会话和任务覆盖保存最新执行状态。 */
    @Override
    public void save(AgentExecutionState state) {
        jdbcTemplate.update("""
                insert into guardian.agent_execution_states
                    (execution_id, conversation_id, diagnostic_task_id, status, turn, tool_calls, last_error, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (conversation_id, diagnostic_task_id) do update set
                    execution_id=excluded.execution_id, status=excluded.status, turn=excluded.turn,
                    tool_calls=excluded.tool_calls, last_error=excluded.last_error, updated_at=excluded.updated_at
                """, state.executionId(), state.conversationId(), state.diagnosticTaskId(), state.status().name(),
                state.turn(), state.toolCalls(), state.lastError(), Timestamp.from(state.updatedAt()));
    }

    /** 恢复同一会话和诊断任务的最新游标。 */
    @Override
    public Optional<AgentExecutionState> find(String conversationId, UUID diagnosticTaskId) {
        List<AgentExecutionState> states = jdbcTemplate.query("""
                select execution_id, conversation_id, diagnostic_task_id, status, turn, tool_calls, last_error, updated_at
                  from guardian.agent_execution_states where conversation_id=? and diagnostic_task_id=?
                """, (rs, row) -> new AgentExecutionState(UUID.fromString(rs.getString("execution_id")),
                rs.getString("conversation_id"), UUID.fromString(rs.getString("diagnostic_task_id")),
                AgentExecutionState.Status.valueOf(rs.getString("status")), rs.getInt("turn"),
                rs.getInt("tool_calls"), rs.getString("last_error"), rs.getTimestamp("updated_at").toInstant()),
                conversationId, diagnosticTaskId);
        return states.stream().findFirst();
    }
}
