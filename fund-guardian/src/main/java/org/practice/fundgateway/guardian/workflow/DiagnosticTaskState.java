package org.practice.fundgateway.guardian.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView.ReviewView;

/** 表示诊断聚合在仓储边界上的完整持久状态。 */
public record DiagnosticTaskState(
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
        List<GovernanceSimulationRecord> simulations,
        Set<String> completedOperations) {

    /** 固定聚合中的集合，避免持久化过程修改领域事实。 */
    public DiagnosticTaskState {
        timeline = List.copyOf(timeline == null ? List.of() : timeline);
        simulations = List.copyOf(simulations == null ? List.of() : simulations);
        completedOperations = Set.copyOf(completedOperations == null ? Set.of() : completedOperations);
    }
}
