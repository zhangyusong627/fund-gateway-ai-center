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

    /** 依靠文档版本唯一约束幂等创建任务。 */
    @Override
    public IndexTask create(IndexTask task) {
        int inserted = jdbcTemplate.update("insert into knowledge.knowledge_index_tasks "
                        + "(task_id, document_id, version, status, error_message, updated_at) "
                        + "values (?, ?, ?, ?, ?, now()) on conflict (document_id, version) do nothing",
                task.taskId(), task.documentId(), task.version(), task.status().name(), task.errorMessage());
        if (inserted == 1) {
            return task;
        }
        return jdbcTemplate.query("select task_id, document_id, version, status, error_message, updated_at "
                        + "from knowledge.knowledge_index_tasks where document_id=? and version=?", rowMapper(),
                task.documentId(), task.version()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("索引任务并发创建后读取失败"));
    }

    /** 通过带原状态条件的 UPDATE 原子抢占任务。 */
    @Override
    public Optional<IndexTask> claim(UUID taskId) {
        int updated = jdbcTemplate.update("update knowledge.knowledge_index_tasks set status=case "
                        + "when status='CREATED' then 'PARSING' else 'INDEXING' end, "
                        + "error_message=null, updated_at=now() where task_id=? and status in ('CREATED','PARSED')",
                taskId);
        return updated == 1 ? find(taskId) : Optional.empty();
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
