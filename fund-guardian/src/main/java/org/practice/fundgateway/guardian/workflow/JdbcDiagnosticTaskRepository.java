package org.practice.fundgateway.guardian.workflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView.ReviewView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 使用 PostgreSQL 规范化表保存诊断任务及其审批和模拟治理事实。 */
public class JdbcDiagnosticTaskRepository implements DiagnosticTaskRepository {

    static final String INSERT_TASK_SQL = "insert into guardian.diagnosis_workflow_tasks "
            + "(task_id, creation_key, snapshot_id, risk_fingerprint, status, gate_status, gate_reason, "
            + "report_json, snapshot_json, created_at, updated_at, review_deadline) values (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?) "
            + "on conflict (creation_key) do nothing";
    static final String UPDATE_TASK_SQL = "update guardian.diagnosis_workflow_tasks set status=?, "
            + "updated_at=?, gate_status=?, gate_reason=?, report_json=?::jsonb, snapshot_json=?::jsonb, review_deadline=? "
            + "where task_id=? and status=?";

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper mapper;

    /** 使用默认 JSON 映射器创建 PostgreSQL 仓储。 */
    public JdbcDiagnosticTaskRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, JsonMapper.builder().build());
    }

    /** 注入 JDBC 和 JSON 映射依赖，便于替身测试。 */
    public JdbcDiagnosticTaskRepository(JdbcTemplate jdbcTemplate, JsonMapper mapper) {
        if (jdbcTemplate == null || mapper == null) {
            throw new IllegalArgumentException("JDBC 诊断仓储依赖不能为空");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
    }

    /** 幂等插入诊断主记录和初始时间线，冲突时读取原任务。 */
    @Override
    @Transactional
    public DiagnosticTask saveIfAbsent(String creationKey, DiagnosticTask task) {
        DiagnosticTaskState state = task.state();
        int inserted = jdbcTemplate.update(INSERT_TASK_SQL, state.taskId(), creationKey, state.snapshotId(),
                state.riskFingerprint(), state.status().name(), state.gateStatus().name(), state.gateReason(),
                writeJson(state.report()), writeJson(state.snapshot()), Timestamp.from(state.createdAt()), Timestamp.from(state.updatedAt()),
                timestamp(state.reviewDeadline()));
        if (inserted == 0) {
            return findByCreationKey(creationKey)
                    .or(() -> findActiveByRiskFingerprint(state.riskFingerprint()))
                    .orElseThrow(() -> new DiagnosticWorkflowException(
                            "创建键或活跃风险指纹冲突但未找到已有诊断任务：" + creationKey));
        }
        saveRelatedFacts(state);
        return task;
    }

    /** 更新任务主状态并幂等追加时间线、审批和模拟治理记录。 */
    @Override
    @Transactional
    public void save(DiagnosticTask task) {
        DiagnosticTaskState state = task.state();
        int updated = jdbcTemplate.update(UPDATE_TASK_SQL, state.status().name(), Timestamp.from(state.updatedAt()),
                state.gateStatus().name(), state.gateReason(), writeJson(state.report()), writeJson(state.snapshot()),
                timestamp(state.reviewDeadline()), state.taskId(), previousStatus(state.status()).name());
        if (updated == 0) {
            throw new DiagnosticWorkflowException("诊断任务不存在或已被其他操作更新：" + state.taskId());
        }
        saveRelatedFacts(state);
    }

    /** 按创建幂等键加载诊断聚合。 */
    @Override
    public Optional<DiagnosticTask> findByCreationKey(String creationKey) {
        return findOne("select * from guardian.diagnosis_workflow_tasks where creation_key=?", creationKey);
    }

    /** 按风险指纹查询尚未结束的诊断任务，活跃唯一性由数据库部分索引保证。 */
    @Override
    public Optional<DiagnosticTask> findActiveByRiskFingerprint(String riskFingerprint) {
        return findOne("select * from guardian.diagnosis_workflow_tasks "
                + "where risk_fingerprint=? and status in ('DIAGNOSED','PENDING_APPROVAL','APPROVED') "
                + "order by created_at limit 1", riskFingerprint);
    }

    /** 按任务标识加载诊断聚合。 */
    @Override
    public Optional<DiagnosticTask> findById(UUID taskId) {
        return findOne("select * from guardian.diagnosis_workflow_tasks where task_id=?", taskId);
    }

    /** 按创建时间查询并恢复全部诊断聚合。 */
    @Override
    public List<DiagnosticTask> findAll() {
        List<TaskRow> rows = jdbcTemplate.query(
                "select * from guardian.diagnosis_workflow_tasks order by created_at", this::mapTaskRow);
        return rows.stream().map(this::hydrate).toList();
    }

    /** 查询一条主记录并加载相关事实。 */
    private Optional<DiagnosticTask> findOne(String sql, Object parameter) {
        List<TaskRow> rows = jdbcTemplate.query(sql, this::mapTaskRow, parameter);
        return rows.stream().findFirst().map(this::hydrate);
    }

    /** 将数据库主记录映射为尚未装配子记录的中间对象。 */
    TaskRow mapTaskRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TaskRow(UUID.fromString(resultSet.getString("task_id")),
                resultSet.getString("creation_key"), resultSet.getString("snapshot_id"),
                resultSet.getString("risk_fingerprint"),
                DiagnosticTaskStatus.valueOf(resultSet.getString("status")),
                GateStatus.valueOf(resultSet.getString("gate_status")), resultSet.getString("gate_reason"),
                readJson(resultSet.getString("report_json"), ModelDiagnosisReport.class),
                instant(resultSet, "created_at"), instant(resultSet, "updated_at"),
                nullableInstant(resultSet, "review_deadline"),
                readJsonNullable(resultSet.getString("snapshot_json"), org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot.class));
    }

    /** 装配时间线、审批和治理模拟记录并恢复领域聚合。 */
    private DiagnosticTask hydrate(TaskRow row) {
        List<DiagnosticTimelineEvent> timeline = jdbcTemplate.query(
                "select * from guardian.diagnosis_timeline where task_id=? order by event_sequence",
                this::mapTimeline, row.taskId());
        List<ApprovalRow> approvals = jdbcTemplate.query(
                "select * from guardian.diagnosis_approvals where task_id=?", this::mapApproval, row.taskId());
        List<GovernanceSimulationRecord> simulations = jdbcTemplate.query(
                "select * from guardian.governance_simulations where task_id=? order by simulated_at",
                this::mapSimulation, row.taskId());
        ReviewView review = approvals.isEmpty() ? null : approvals.get(0).review();
        String reviewOperationId = approvals.isEmpty() ? null : approvals.get(0).operationId();
        Set<String> operations = new HashSet<>();
        if (reviewOperationId != null) {
            operations.add(reviewOperationId);
        }
        simulations.stream().map(GovernanceSimulationRecord::operationId).forEach(operations::add);
        return DiagnosticTask.restore(new DiagnosticTaskState(row.taskId(), row.creationKey(), row.snapshotId(),
                row.riskFingerprint(), row.status(), row.gateStatus(), row.gateReason(), row.report(), row.createdAt(),
                row.updatedAt(), row.reviewDeadline(), review, reviewOperationId, timeline, simulations, operations,
                row.snapshot()));
    }

    /** 保存聚合下属的追加型事实。 */
    private void saveRelatedFacts(DiagnosticTaskState state) {
        for (DiagnosticTimelineEvent event : state.timeline()) {
            jdbcTemplate.update("insert into guardian.diagnosis_timeline "
                            + "(task_id,event_sequence,event_type,operator_name,detail,occurred_at) values (?,?,?,?,?,?) "
                            + "on conflict (task_id,event_sequence) do nothing",
                    state.taskId(), event.sequence(), event.eventType(), event.operator(), event.detail(),
                    Timestamp.from(event.occurredAt()));
        }
        if (state.review() != null) {
            jdbcTemplate.update("insert into guardian.diagnosis_approvals "
                            + "(task_id,operation_id,action,reviewer,comment,reviewed_at) values (?,?,?,?,?,?) "
                            + "on conflict (task_id) do nothing",
                    state.taskId(), state.reviewOperationId(), state.review().action().name(),
                    state.review().reviewer(), state.review().comment(), Timestamp.from(state.review().reviewedAt()));
        }
        for (GovernanceSimulationRecord simulation : state.simulations()) {
            jdbcTemplate.update("insert into guardian.governance_simulations "
                            + "(simulation_id,task_id,operation_id,action_type,parameters_json,result,operator_name,simulated_at) "
                            + "values (?,?,?,?,?::jsonb,?,?,?) on conflict (task_id,operation_id) do nothing",
                    simulation.simulationId(), state.taskId(), simulation.operationId(), simulation.actionType(),
                    writeJson(simulation.parameters()), simulation.result(), simulation.operator(),
                    Timestamp.from(simulation.simulatedAt()));
        }
    }

    /** 映射一条诊断时间线记录。 */
    private DiagnosticTimelineEvent mapTimeline(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DiagnosticTimelineEvent(resultSet.getLong("event_sequence"),
                resultSet.getString("event_type"), resultSet.getString("operator_name"),
                resultSet.getString("detail"), instant(resultSet, "occurred_at"));
    }

    /** 映射一条人工审批记录。 */
    private ApprovalRow mapApproval(ResultSet resultSet, int rowNumber) throws SQLException {
        ReviewView review = new ReviewView(ReviewAction.valueOf(resultSet.getString("action")),
                resultSet.getString("reviewer"), resultSet.getString("comment"),
                instant(resultSet, "reviewed_at"));
        return new ApprovalRow(resultSet.getString("operation_id"), review);
    }

    /** 映射一条模拟治理记录。 */
    private GovernanceSimulationRecord mapSimulation(ResultSet resultSet, int rowNumber) throws SQLException {
        Map<String, String> parameters = readJson(resultSet.getString("parameters_json"),
                new TypeReference<Map<String, String>>() { });
        return new GovernanceSimulationRecord(UUID.fromString(resultSet.getString("simulation_id")),
                resultSet.getString("operation_id"), resultSet.getString("action_type"), parameters,
                resultSet.getString("result"), resultSet.getString("operator_name"),
                instant(resultSet, "simulated_at"));
    }

    /** 将对象编码为 PostgreSQL JSONB 所需文本。 */
    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("诊断持久状态 JSON 编码失败", exception);
        }
    }

    /** 将 JSON 文本解码为指定类型。 */
    private <T> T readJson(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("诊断持久状态 JSON 解码失败", exception);
        }
    }

    /** 读取兼容旧记录的可空 JSON。 */
    private <T> T readJsonNullable(String value, Class<T> type) {
        return value == null ? null : readJson(value, type);
    }

    /** 将 JSON 文本解码为带泛型的指定类型。 */
    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("诊断持久状态 JSON 解码失败", exception);
        }
    }

    /** 读取非空数据库时间。 */
    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    /** 读取可为空的数据库时间。 */
    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** 将可为空的领域时间转换为 JDBC 时间。 */
    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    /** 根据目标状态确定 PostgreSQL 条件更新所需的前置状态。 */
    private DiagnosticTaskStatus previousStatus(DiagnosticTaskStatus status) {
        if (status == DiagnosticTaskStatus.SIMULATED) {
            return DiagnosticTaskStatus.APPROVED;
        }
        if (status == DiagnosticTaskStatus.APPROVED || status == DiagnosticTaskStatus.REJECTED
                || status == DiagnosticTaskStatus.RETURNED || status == DiagnosticTaskStatus.EXPIRED) {
            return DiagnosticTaskStatus.PENDING_APPROVAL;
        }
        throw new DiagnosticWorkflowException("当前状态没有可持久化的转换前态：" + status);
    }

    /** 保存诊断任务主表的一行中间映射。 */
    record TaskRow(UUID taskId, String creationKey, String snapshotId, String riskFingerprint,
                   DiagnosticTaskStatus status, GateStatus gateStatus, String gateReason,
                   ModelDiagnosisReport report, Instant createdAt, Instant updatedAt,
                   Instant reviewDeadline, org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot snapshot) {
    }

    /** 保存审批操作号及其查询视图。 */
    private record ApprovalRow(String operationId, ReviewView review) {
    }
}
