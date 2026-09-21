package org.practice.fundgateway.guardian.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;

/** 为可视化后台提供稳定且不可修改的诊断任务查询模型。 */
public record DiagnosticTaskView(
        UUID taskId,
        String creationKey,
        String snapshotId,
        String riskFingerprint,
        DiagnosticTaskStatus status,
        GateStatus gateStatus,
        String gateReason,
        ModelDiagnosisReport report,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewDeadline,
        ReviewView review,
        String reviewOperationId,
        List<DiagnosticTimelineEvent> timeline,
        List<GovernanceSimulationRecord> simulations) {

    /** 固定查询模型中的时间线和治理记录。 */
    public DiagnosticTaskView {
        timeline = List.copyOf(timeline == null ? List.of() : timeline);
        simulations = List.copyOf(simulations == null ? List.of() : simulations);
    }

    /** 表示最近一次人工审批结果。 */
    public record ReviewView(ReviewAction action, String reviewer, String comment, Instant reviewedAt) {
    }
}
