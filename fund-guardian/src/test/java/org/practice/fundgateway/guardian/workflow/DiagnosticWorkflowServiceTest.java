package org.practice.fundgateway.guardian.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.practice.fundgateway.common.permission.InMemoryPermissionAuditRecorder;
import org.practice.fundgateway.common.permission.PermissionAuditEvent;
import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.common.permission.PermissionDeniedException;
import org.practice.fundgateway.common.permission.PermissionGuard;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.ModelFinding;
import org.practice.fundgateway.guardian.diagnosis.RuleFinding;

/** 验证诊断持久状态、人工审批和模拟治理的完整内存闭环。 */
class DiagnosticWorkflowServiceTest {

    private MutableClock clock;
    private DiagnosticWorkflowService service;

    /** 为每个用例创建隔离的内存仓储和可推进时钟。 */
    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-20T00:00:00Z"));
        service = new DiagnosticWorkflowService(new InMemoryDiagnosticTaskRepository(),
                new ModelDiagnosisGate(), clock, Duration.ofMinutes(15));
    }

    /** 规则和模型硬冲突时应创建待审批任务并记录完整时间线。 */
    @Test
    void shouldRouteHardConflictToApproval() {
        DiagnosticTaskView task = service.create("request-1", snapshot(true), report(false, false));

        assertEquals(DiagnosticTaskStatus.PENDING_APPROVAL, task.status());
        assertEquals(ModelDiagnosisGate.GateStatus.HUMAN_REVIEW, task.gateStatus());
        assertEquals(3, task.timeline().size());
        assertEquals("APPROVAL_REQUESTED", task.timeline().get(2).eventType());
        assertNotNull(task.reviewDeadline());
    }

    /** 模型与规则一致且未要求人工介入时应直接形成已诊断查询结果。 */
    @Test
    void shouldKeepConsistentReportAsDiagnosed() {
        DiagnosticTaskView task = service.create("request-2", snapshot(true), report(true, false));

        assertEquals(DiagnosticTaskStatus.DIAGNOSED, task.status());
        assertEquals(2, task.timeline().size());
        assertEquals(null, task.reviewDeadline());
    }

    /** 同一活跃风险在不同创建键下也只能复用已有任务。 */
    @Test
    void shouldReuseActiveTaskForSameRiskFingerprint() {
        DiagnosticTaskView first = service.create("request-active-1", snapshot(true), report(true, false));
        DiagnosticTaskView second = service.create("request-active-2", snapshot(true), report(true, false));

        assertEquals(first.taskId(), second.taskId());
        assertEquals(1, service.findAll(null).size());
    }

    /** 活跃任务进入拒绝终态后，同一风险可以重新开案。 */
    @Test
    void shouldReopenRiskAfterTerminalTask() {
        DiagnosticTaskView first = service.create("request-reopen-1", snapshot(true), report(false, false));
        service.reject(first.taskId(), "review-reopen", "owner", "本次证据不足");

        DiagnosticTaskView second = service.create("request-reopen-2", snapshot(true), report(false, false));

        assertEquals(DiagnosticTaskStatus.PENDING_APPROVAL, second.status());
        assertNotNull(second.taskId());
        assertEquals(2, service.findAll(null).size());
    }

    /** 相同创建键和审批操作号应保持幂等，不增加任务或时间线。 */
    @Test
    void shouldProtectDuplicateCreationAndApproval() {
        DiagnosticTaskView first = service.create("request-3", snapshot(true), report(false, false));
        DiagnosticTaskView duplicate = service.create("request-3", null, null);
        assertEquals(first.taskId(), duplicate.taskId());
        assertEquals(1, service.findAll(null).size());

        DiagnosticTaskView approved = service.approve(first.taskId(), "review-1", "owner", "批准模拟验证");
        DiagnosticTaskView repeated = service.approve(first.taskId(), "review-1", "owner", "重复请求");

        assertEquals(DiagnosticTaskStatus.APPROVED, repeated.status());
        assertEquals(approved.timeline().size(), repeated.timeline().size());
    }

    /** 同一任务已完成审批后使用新操作号再次审批应被拒绝。 */
    @Test
    void shouldRejectSecondReviewWithDifferentOperation() {
        DiagnosticTaskView task = service.create("request-4", snapshot(true), report(false, false));
        service.reject(task.taskId(), "review-1", "owner", "证据不足");

        assertThrows(DiagnosticWorkflowException.class,
                () -> service.approve(task.taskId(), "review-2", "owner", "重新批准"));
        assertEquals(DiagnosticTaskStatus.REJECTED, service.findById(task.taskId()).orElseThrow().status());
    }

    /** 退回动作应形成独立终态并保留审批原因。 */
    @Test
    void shouldReturnTaskForRevision() {
        DiagnosticTaskView task = service.create("request-5", snapshot(true), report(false, false));

        DiagnosticTaskView returned = service.returnForRevision(
                task.taskId(), "review-return", "owner", "补充线程池证据");

        assertEquals(DiagnosticTaskStatus.RETURNED, returned.status());
        assertEquals(ReviewAction.RETURN, returned.review().action());
        assertEquals("补充线程池证据", returned.review().comment());
    }

    /** 超过审批期限后审批应失败，并把任务标记为已过期。 */
    @Test
    void shouldProtectExpiredApproval() {
        DiagnosticTaskView task = service.create("request-6", snapshot(true), report(false, false));
        clock.advance(Duration.ofMinutes(16));

        assertThrows(DiagnosticWorkflowException.class,
                () -> service.approve(task.taskId(), "late-review", "owner", "超时批准"));
        DiagnosticTaskView expired = service.findById(task.taskId()).orElseThrow();
        assertEquals(DiagnosticTaskStatus.EXPIRED, expired.status());
        assertEquals("APPROVAL_EXPIRED", expired.timeline().get(expired.timeline().size() - 1).eventType());
    }

    /** 已批准任务可以完成一次幂等的模拟治理并形成审计记录。 */
    @Test
    void shouldRecordGovernanceSimulationIdempotently() {
        DiagnosticTaskView task = service.create("request-7", snapshot(true), report(false, false));
        service.approve(task.taskId(), "review-approve", "owner", "允许模拟限流");

        DiagnosticTaskView simulated = service.simulateGovernance(task.taskId(), "simulation-1",
                "ADJUST_RATE_LIMIT", Map.of("qps", "80"), "owner");
        DiagnosticTaskView repeated = service.simulateGovernance(task.taskId(), "simulation-1",
                "ADJUST_RATE_LIMIT", Map.of("qps", "80"), "owner");

        assertEquals(DiagnosticTaskStatus.SIMULATED, repeated.status());
        assertEquals(1, repeated.simulations().size());
        assertEquals("SIMULATED_SUCCESS", simulated.simulations().get(0).result());
        assertEquals(1, service.findAll(DiagnosticTaskStatus.SIMULATED).size());
    }

    /** 超过审批期限后，查询入口应自动把待审批任务置为已过期。 */
    @Test
    void shouldExpireOverdueTaskOnQuery() {
        DiagnosticTaskView task = service.create("request-9", snapshot(true), report(false, false));
        clock.advance(Duration.ofMinutes(16));

        DiagnosticTaskView expired = service.findById(task.taskId()).orElseThrow();

        assertEquals(DiagnosticTaskStatus.EXPIRED, expired.status());
        assertEquals("APPROVAL_EXPIRED", expired.timeline().get(expired.timeline().size() - 1).eventType());
        assertEquals(0, service.findAll(DiagnosticTaskStatus.PENDING_APPROVAL).size());
    }

    /** 同指纹任务自动过期后再次创建应生成新任务，不再复用过期任务。 */
    @Test
    void shouldCreateNewTaskAfterFingerprintExpired() {
        DiagnosticTaskView first = service.create("request-10", snapshot(true), report(false, false));
        clock.advance(Duration.ofMinutes(16));

        DiagnosticTaskView second = service.create("request-11", snapshot(true), report(false, false));

        assertNotEquals(first.taskId(), second.taskId());
        assertEquals(DiagnosticTaskStatus.PENDING_APPROVAL, second.status());
        assertEquals(DiagnosticTaskStatus.EXPIRED, service.findById(first.taskId()).orElseThrow().status());
    }

    /** 未经人工批准的任务不得执行治理模拟。 */
    @Test
    void shouldRejectSimulationWithoutApproval() {
        DiagnosticTaskView task = service.create("request-8", snapshot(true), report(true, false));

        assertThrows(DiagnosticWorkflowException.class, () -> service.simulateGovernance(
                task.taskId(), "simulation-2", "ADJUST_TIMEOUT", Map.of("timeoutMs", "1000"), "owner"));
    }

    /** 审批权限关闭时，工作流状态不得改变且拒绝结果必须可审计。 */
    @Test
    void shouldRejectReviewWithoutPermissionAndKeepTaskPending() {
        InMemoryPermissionAuditRecorder recorder = new InMemoryPermissionAuditRecorder();
        DiagnosticWorkflowService guardedService = new DiagnosticWorkflowService(
                new InMemoryDiagnosticTaskRepository(), new ModelDiagnosisGate(), clock,
                Duration.ofMinutes(15), new PermissionGuard(recorder));
        DiagnosticTaskView task = guardedService.create("request-permission", snapshot(true), report(false, false));
        PermissionContext noApproval = new PermissionContext("read-only-agent", Set.of("synthetic-provider"),
                Set.of(new PermissionContext.KnowledgeScope("*", "*", "*")),
                PermissionContext.SYNTHETIC_DIAGNOSTIC_TOOLS, false);

        assertThrows(PermissionDeniedException.class, () -> guardedService.approve(task.taskId(),
                "review-forbidden", "read-only-agent", "尝试审批", noApproval));

        assertEquals(DiagnosticTaskStatus.PENDING_APPROVAL,
                guardedService.findById(task.taskId()).orElseThrow().status());
        assertEquals(PermissionAuditEvent.Decision.DENIED, recorder.snapshot().getLast().decision());
    }

    /** 构造包含一条确定性规则的固定诊断快照。 */
    private DiagnosisSnapshot snapshot(boolean matched) {
        return new DiagnosisSnapshot("snapshot-1", clock.instant(), null,
                List.of(new RuleFinding("R001", matched, "HIGH", "QPS 超过契约阈值", "执行人工核查", true)),
                null, List.of(), "fingerprint-1");
    }

    /** 构造模型诊断报告。 */
    private ModelDiagnosisReport report(boolean matched, boolean requiresHumanReview) {
        return new ModelDiagnosisReport("发现接口调用风险", "HIGH",
                List.of(new ModelFinding("R001", matched, "指标显示调用量异常", "建议人工核查", false)),
                requiresHumanReview);
    }

    /** 提供测试可控的 Java 时钟。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        /** 创建指定初始时间的时钟。 */
        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /** 返回固定的 UTC 时区。 */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** 当前测试不需要切换时区，直接返回自身。 */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** 返回当前可控时间点。 */
        @Override
        public Instant instant() {
            return instant;
        }

        /** 推进当前测试时间。 */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
