package org.practice.fundgateway.knowledge.document;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** 使用 PostgreSQL 保存索引任务状态。 */
public class PostgresIndexTaskRepository implements IndexTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 注入 JDBC 模板。 */
    public PostgresIndexTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 保存任务状态，状态更新由应用服务控制。 */
    @Override
    public IndexTask save(IndexTask task) {
        jdbcTemplate.update("insert into knowledge.knowledge_index_tasks (task_id, document_id, version, status, error_message, updated_at) values (?, ?, ?, ?, ?, now()) on conflict (task_id) do update set status=excluded.status, error_message=excluded.error_message, updated_at=now()", task.taskId(), task.documentId(), task.version(), task.status().name(), task.errorMessage());
        return task;
    }

    /** 按任务标识读取状态。 */
    @Override
    public Optional<IndexTask> find(UUID taskId) {
        return jdbcTemplate.query("select task_id, document_id, version, status, error_message, updated_at from knowledge.knowledge_index_tasks where task_id=?", rowMapper(), taskId).stream().findFirst();
    }

    /** 查询全部索引任务。 */
    @Override
    public List<IndexTask> findAll() {
        return jdbcTemplate.query("select task_id, document_id, version, status, error_message, updated_at from knowledge.knowledge_index_tasks order by updated_at", rowMapper());
    }

    /** 将数据库行转换为索引任务。 */
    private org.springframework.jdbc.core.RowMapper<IndexTask> rowMapper() {
        return (resultSet, rowNumber) -> new IndexTask(UUID.fromString(resultSet.getString("task_id")),
                resultSet.getString("document_id"), resultSet.getString("version"),
                IndexTaskStatus.valueOf(resultSet.getString("status")), resultSet.getString("error_message"),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
