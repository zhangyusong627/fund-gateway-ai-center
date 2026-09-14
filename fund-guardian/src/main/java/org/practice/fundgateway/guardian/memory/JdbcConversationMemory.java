package org.practice.fundgateway.guardian.memory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** 使用 PostgreSQL 保存会话消息和摘要的基础设施适配器。 */
public class JdbcConversationMemory implements ConversationMemoryPort {

    private final JdbcTemplate jdbcTemplate;

    /** 注入数据库访问模板。 */
    public JdbcConversationMemory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 读取同一会话、同一诊断任务下未过期的最近消息。 */
    @Override
    public List<MemoryMessage> loadRecent(String conversationId, UUID diagnosticTaskId, int limit) {
        if (limit < 1) throw new IllegalArgumentException("记忆读取条数必须大于零");
        return jdbcTemplate.query("""
                select message_id, conversation_id, diagnostic_task_id, role, message_type,
                       content, created_at, expires_at
                  from guardian.conversation_memory_messages
                 where conversation_id = ? and diagnostic_task_id = ?
                   and (expires_at is null or expires_at > now())
                 order by created_at desc
                 limit ?
                """, (rs, rowNum) -> new MemoryMessage(
                UUID.fromString(rs.getString("message_id")), rs.getString("conversation_id"),
                UUID.fromString(rs.getString("diagnostic_task_id")),
                MemoryMessage.Role.valueOf(rs.getString("role")),
                MemoryMessage.MessageType.valueOf(rs.getString("message_type")),
                rs.getString("content"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()),
                conversationId, diagnosticTaskId, limit).stream().sorted(java.util.Comparator.comparing(MemoryMessage::createdAt)).toList();
    }

    /** 插入一条消息，重复 messageId 直接保持幂等。 */
    @Override
    public void append(MemoryMessage message) {
        jdbcTemplate.update("""
                insert into guardian.conversation_memory_messages
                    (message_id, conversation_id, diagnostic_task_id, role, message_type,
                     content, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (message_id) do nothing
                """, message.messageId(), message.conversationId(), message.diagnosticTaskId(),
                message.role().name(), message.messageType().name(), message.content(),
                Timestamp.from(message.createdAt()), message.expiresAt() == null ? null : Timestamp.from(message.expiresAt()));
    }

    /** 插入摘要版本，重复版本保持幂等。 */
    @Override
    public void saveSummary(MemorySummary summary) {
        jdbcTemplate.update("""
                insert into guardian.conversation_memory_summaries
                    (conversation_id, diagnostic_task_id, summary_version, content, created_at)
                values (?, ?, ?, ?, ?)
                on conflict (conversation_id, diagnostic_task_id, summary_version) do update
                    set content = excluded.content, created_at = excluded.created_at
                """, summary.conversationId(), summary.diagnosticTaskId(), summary.version(),
                summary.content(), Timestamp.from(summary.createdAt()));
    }

    /** 读取最新摘要版本。 */
    @Override
    public MemorySummary loadLatestSummary(String conversationId, UUID diagnosticTaskId) {
        return jdbcTemplate.query("""
                select conversation_id, diagnostic_task_id, summary_version, content, created_at
                  from guardian.conversation_memory_summaries
                 where conversation_id = ? and diagnostic_task_id = ?
                 order by summary_version desc limit 1
                """, (rs, rowNum) -> new MemorySummary(rs.getString("conversation_id"),
                UUID.fromString(rs.getString("diagnostic_task_id")), rs.getInt("summary_version"),
                rs.getString("content"), rs.getTimestamp("created_at").toInstant()),
                conversationId, diagnosticTaskId).stream().findFirst().orElse(null);
    }
}
