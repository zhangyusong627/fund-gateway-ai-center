package org.practice.fundgateway.guardian.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateDecision;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.ModelFinding;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

/** 使用 JDBC 替身和行映射验证诊断 PostgreSQL 仓储，不连接真实数据库。 */
class JdbcDiagnosticTaskRepositoryTest {

    /** 新任务应写入一条主记录和三条初始时间线记录。 */
    @Test
    void shouldWriteTaskAndInitialTimelineWithJdbcSubstitute() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcDiagnosticTaskRepository repository = new JdbcDiagnosticTaskRepository(jdbcTemplate);

        DiagnosticTask saved = repository.saveIfAbsent("create-1", pendingTask());

        assertEquals(DiagnosticTaskStatus.PENDING_APPROVAL, saved.toView().status());
        verify(jdbcTemplate).update(eq(JdbcDiagnosticTaskRepository.INSERT_TASK_SQL), any(Object[].class));
        verify(jdbcTemplate, times(4)).update(anyString(), any(Object[].class));
    }

    /** 主表行应恢复任务标识、状态、模型报告和审批期限。 */
    @Test
    void shouldMapTaskMainRow() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Instant createdAt = Instant.parse("2026-09-20T00:00:00Z");
        Instant deadline = createdAt.plusSeconds(900);
        ModelDiagnosisReport report = report();
        when(resultSet.getString("task_id")).thenReturn("df65c1d0-70e8-4b05-8e97-8dd4829b26f7");
        when(resultSet.getString("creation_key")).thenReturn("create-1");
        when(resultSet.getString("snapshot_id")).thenReturn("snapshot-1");
        when(resultSet.getString("risk_fingerprint")).thenReturn("fingerprint-1");
        when(resultSet.getString("status")).thenReturn("PENDING_APPROVAL");
        when(resultSet.getString("gate_status")).thenReturn("HUMAN_REVIEW");
        when(resultSet.getString("gate_reason")).thenReturn("模型结论与确定性规则冲突：R001");
        when(resultSet.getString("report_json")).thenReturn(JsonMapper.builder().build().writeValueAsString(report));
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(createdAt));
        when(resultSet.getTimestamp("review_deadline")).thenReturn(Timestamp.from(deadline));

        JdbcDiagnosticTaskRepository.TaskRow row = new JdbcDiagnosticTaskRepository(
                mock(JdbcTemplate.class)).mapTaskRow(resultSet, 0);

        assertEquals(DiagnosticTaskStatus.PENDING_APPROVAL, row.status());
        assertEquals("发现接口调用风险", row.report().summary());
        assertEquals(deadline, row.reviewDeadline());
    }

    /** 构造待人工审批的诊断聚合。 */
    private DiagnosticTask pendingTask() {
        Instant now = Instant.parse("2026-09-20T00:00:00Z");
        return new DiagnosticTask(UUID.fromString("df65c1d0-70e8-4b05-8e97-8dd4829b26f7"),
                "create-1", "snapshot-1", "fingerprint-1", report(),
                new GateDecision(GateStatus.HUMAN_REVIEW, "模型结论与确定性规则冲突：R001"),
                now, now.plusSeconds(900));
    }

    /** 构造包含固定规则发现的模型报告。 */
    private ModelDiagnosisReport report() {
        return new ModelDiagnosisReport("发现接口调用风险", "HIGH",
                List.of(new ModelFinding("R001", false, "指标出现冲突", "转人工核查", true)), true);
    }
}
