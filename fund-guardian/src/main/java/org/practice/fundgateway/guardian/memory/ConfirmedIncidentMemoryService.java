package org.practice.fundgateway.guardian.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskStatus;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView;

/** 只把人工批准且有证据的诊断沉淀为长期案例记忆。 */
public class ConfirmedIncidentMemoryService {

    private final IncidentMemoryPort memoryPort;
    private final Clock clock;

    /** 创建案例记忆服务。 */
    public ConfirmedIncidentMemoryService(IncidentMemoryPort memoryPort) {
        this(memoryPort, Clock.systemUTC());
    }

    /** 注入时钟，便于验证案例创建时间。 */
    public ConfirmedIncidentMemoryService(IncidentMemoryPort memoryPort, Clock clock) {
        if (memoryPort == null || clock == null) throw new IllegalArgumentException("案例记忆依赖不能为空");
        this.memoryPort = memoryPort;
        this.clock = clock;
    }

    /** 审批通过后从当前快照和报告创建案例，其他状态直接拒绝。 */
    public ConfirmedIncidentMemory promote(DiagnosticTaskView task, DiagnosisSnapshot snapshot,
                                           String approvalId) {
        if (task == null || task.status() != DiagnosticTaskStatus.APPROVED) {
            throw new IllegalStateException("只有已批准诊断才能沉淀长期案例");
        }
        if (snapshot == null || task.report() == null || approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("案例记忆缺少诊断报告、快照或审批标识");
        }
        if (snapshot.metrics() == null || snapshot.metrics().provider() == null
                || snapshot.metrics().interfaceName() == null) {
            throw new IllegalArgumentException("案例记忆缺少资方或接口标识");
        }
        ModelDiagnosisReport report = task.report();
        List<String> evidenceRefs = new ArrayList<>();
        snapshot.ragCitations().forEach(citation -> evidenceRefs.add(citation.citationId()));
        report.findings().forEach(finding -> evidenceRefs.add("rule:" + finding.ruleId()));
        ConfirmedIncidentMemory result = new ConfirmedIncidentMemory(UUID.randomUUID(), task.taskId(),
                snapshot.metrics().provider(), snapshot.metrics().interfaceName(),
                snapshot.metrics().toString(), report.summary(), evidenceRefs, approvalId,
                "适用于当前资方、接口和契约版本", ConfirmedIncidentMemory.Status.ACTIVE, 1,
                Instant.now(clock));
        memoryPort.save(result);
        return result;
    }
}
