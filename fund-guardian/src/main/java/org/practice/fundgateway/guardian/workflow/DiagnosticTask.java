package org.practice.fundgateway.guardian.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateDecision;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView.ReviewView;

/** 聚合诊断状态、审批、时间线和治理模拟，并保护所有状态转换。 */
public class DiagnosticTask {

    private final UUID taskId;
    private final String creationKey;
    private final String snapshotId;
    private final String riskFingerprint;
    private final GateStatus gateStatus;
    private final String gateReason;
    private final ModelDiagnosisReport report;
    private final DiagnosisSnapshot snapshot;
    private final Instant createdAt;
    private final Instant reviewDeadline;
    private final List<DiagnosticTimelineEvent> timeline = new ArrayList<>();
    private final List<GovernanceSimulationRecord> simulations = new ArrayList<>();
    private final Set<String> completedOperations = new HashSet<>();
    private DiagnosticTaskStatus status;
    private Instant updatedAt;
    private ReviewView review;
    private String reviewOperationId;

    /** 根据模型门禁结论创建诊断任务，硬冲突和人工标记都进入审批。 */
    public DiagnosticTask(UUID taskId, String creationKey, String snapshotId, String riskFingerprint,
                          ModelDiagnosisReport report, GateDecision decision, Instant createdAt,
                          Instant reviewDeadline) {
        this(taskId, creationKey, snapshotId, riskFingerprint, report, decision, createdAt, reviewDeadline, null);
    }

    /** 创建带完整诊断事实的可恢复任务。 */
    public DiagnosticTask(UUID taskId, String creationKey, String snapshotId, String riskFingerprint,
                          ModelDiagnosisReport report, GateDecision decision, Instant createdAt,
                          Instant reviewDeadline, DiagnosisSnapshot snapshot) {
        if (taskId == null || isBlank(creationKey) || isBlank(snapshotId) || isBlank(riskFingerprint)
                || report == null || decision == null || createdAt == null) {
            throw new IllegalArgumentException("诊断任务缺少必要字段");
        }
        boolean pendingApproval = decision.status() == GateStatus.HUMAN_REVIEW || report.requiresHumanReview();
        if (pendingApproval && reviewDeadline == null) {
            throw new IllegalArgumentException("待审批任务必须提供审批截止时间");
        }
        this.taskId = taskId;
        this.creationKey = creationKey;
        this.snapshotId = snapshotId;
        this.riskFingerprint = riskFingerprint;
        this.report = new ModelDiagnosisReport(report.summary(), report.riskLevel(),
                List.copyOf(report.findings()), report.requiresHumanReview());
        this.snapshot = snapshot;
        this.gateStatus = decision.status();
        this.gateReason = decision.reason();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.reviewDeadline = pendingApproval ? reviewDeadline : null;
        this.status = pendingApproval ? DiagnosticTaskStatus.PENDING_APPROVAL : DiagnosticTaskStatus.DIAGNOSED;
        append("TASK_CREATED", "SYSTEM", "诊断任务已创建", createdAt);
        append("MODEL_GATE_ASSESSED", "SYSTEM", decision.reason(), createdAt);
        if (pendingApproval) {
            append("APPROVAL_REQUESTED", "SYSTEM", "等待人工审批", createdAt);
        }
    }

    /** 从仓储状态恢复聚合，不重新产生创建时间线事件。 */
    public static DiagnosticTask restore(DiagnosticTaskState state) {
        if (state == null || state.taskId() == null || state.status() == null
                || state.gateStatus() == null || state.report() == null) {
            throw new IllegalArgumentException("诊断任务持久状态不完整");
        }
        return new DiagnosticTask(state);
    }

    /** 使用已校验的仓储状态恢复全部字段。 */
    private DiagnosticTask(DiagnosticTaskState state) {
        this.taskId = state.taskId();
        this.creationKey = state.creationKey();
        this.snapshotId = state.snapshotId();
        this.riskFingerprint = state.riskFingerprint();
        this.status = state.status();
        this.gateStatus = state.gateStatus();
        this.gateReason = state.gateReason();
        this.report = new ModelDiagnosisReport(state.report().summary(), state.report().riskLevel(),
                List.copyOf(state.report().findings()), state.report().requiresHumanReview());
        this.snapshot = state.snapshot();
        this.createdAt = state.createdAt();
        this.updatedAt = state.updatedAt();
        this.reviewDeadline = state.reviewDeadline();
        this.review = state.review();
        this.reviewOperationId = state.reviewOperationId();
        this.timeline.addAll(state.timeline());
        this.simulations.addAll(state.simulations());
        this.completedOperations.addAll(state.completedOperations());
    }

    /** 执行批准、拒绝或退回，并保证相同操作号只生效一次。 */
    public synchronized void review(String operationId, ReviewAction action, String reviewer,
                                    String comment, Instant now) {
        requireOperation(operationId);
        if (completedOperations.contains(operationId)) {
            return;
        }
        if (status != DiagnosticTaskStatus.PENDING_APPROVAL) {
            throw new DiagnosticWorkflowException("当前任务状态不允许审批：" + status);
        }
        if (expireIfOverdue(now)) {
            throw new DiagnosticWorkflowException("审批已过期");
        }
        if (action == null || isBlank(reviewer)) {
            throw new IllegalArgumentException("审批动作和审批人不能为空");
        }
        status = targetStatus(action);
        review = new ReviewView(action, reviewer, comment == null ? "" : comment, now);
        reviewOperationId = operationId;
        updatedAt = now;
        completedOperations.add(operationId);
        append("APPROVAL_" + action.name(), reviewer, review.comment(), now);
    }

    /**
     * 待审批任务超过审批截止时间时置为 EXPIRED。
     * 返回是否发生了状态变化，便于调用方只在真正过期时保存聚合。
     */
    public synchronized boolean expireIfOverdue(Instant now) {
        if (status != DiagnosticTaskStatus.PENDING_APPROVAL || reviewDeadline == null) {
            return false;
        }
        if (now != null && !now.isAfter(reviewDeadline)) {
            return false;
        }
        Instant stamp = now == null ? reviewDeadline : now;
        status = DiagnosticTaskStatus.EXPIRED;
        updatedAt = stamp;
        append("APPROVAL_EXPIRED", "SYSTEM", "审批已超过截止时间", stamp);
        return true;
    }

    /** 在人工批准后写入一条无真实副作用的治理模拟记录。 */
    public synchronized void simulate(String operationId, String actionType, Map<String, String> parameters,
                                      String operator, Instant now) {
        requireOperation(operationId);
        if (completedOperations.contains(operationId)) {
            return;
        }
        if (status != DiagnosticTaskStatus.APPROVED) {
            throw new DiagnosticWorkflowException("只有已批准任务可以执行治理模拟：" + status);
        }
        if (isBlank(actionType) || isBlank(operator) || now == null) {
            throw new IllegalArgumentException("治理动作、操作人和时间不能为空");
        }
        GovernanceSimulationRecord record = new GovernanceSimulationRecord(UUID.randomUUID(), operationId,
                actionType, parameters, "SIMULATED_SUCCESS", operator, now);
        simulations.add(record);
        completedOperations.add(operationId);
        status = DiagnosticTaskStatus.SIMULATED;
        updatedAt = now;
        append("GOVERNANCE_SIMULATED", operator, actionType + " 已模拟执行", now);
    }

    /** 返回可安全交给查询接口的不可变视图。 */
    public synchronized DiagnosticTaskView toView() {
        return new DiagnosticTaskView(taskId, creationKey, snapshotId, riskFingerprint, status,
                gateStatus, gateReason, report, createdAt, updatedAt, reviewDeadline, review,
                timeline, simulations);
    }

    /** 返回任务标识供仓储建立索引。 */
    public UUID taskId() {
        return taskId;
    }

    /** 返回创建任务时固定的诊断快照，供审批后案例沉淀使用。 */
    public DiagnosisSnapshot snapshot() {
        return snapshot;
    }

    /** 返回供仓储保存和恢复的完整状态。 */
    public synchronized DiagnosticTaskState state() {
        return new DiagnosticTaskState(taskId, creationKey, snapshotId, riskFingerprint, status,
                gateStatus, gateReason, report, createdAt, updatedAt, reviewDeadline, review,
                reviewOperationId, timeline, simulations, completedOperations, snapshot);
    }

    /** 将人工动作映射为诊断任务终态。 */
    private DiagnosticTaskStatus targetStatus(ReviewAction action) {
        if (action == ReviewAction.APPROVE) {
            return DiagnosticTaskStatus.APPROVED;
        }
        if (action == ReviewAction.REJECT) {
            return DiagnosticTaskStatus.REJECTED;
        }
        return DiagnosticTaskStatus.RETURNED;
    }

    /** 按连续序号追加一条任务时间线事件。 */
    private void append(String eventType, String operator, String detail, Instant occurredAt) {
        timeline.add(new DiagnosticTimelineEvent(timeline.size() + 1L, eventType, operator, detail, occurredAt));
    }

    /** 校验外部操作的幂等标识。 */
    private void requireOperation(String operationId) {
        if (isBlank(operationId)) {
            throw new IllegalArgumentException("操作号不能为空");
        }
    }

    /** 判断字符串是否缺失有效内容。 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
