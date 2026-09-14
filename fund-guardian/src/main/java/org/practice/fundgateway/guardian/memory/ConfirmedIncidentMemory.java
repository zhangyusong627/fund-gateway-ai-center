package org.practice.fundgateway.guardian.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 表示经过人工确认、可作为补充证据召回的历史故障案例。 */
public record ConfirmedIncidentMemory(UUID caseId, UUID sourceTaskId, String providerId,
                                      String interfaceId, String symptom, String confirmedRootCause,
                                      List<String> evidenceRefs, String approvalId,
                                      String applicableConditions, Status status, int version,
                                      Instant createdAt) {

    /** 只允许完整的已确认案例进入长期记忆。 */
    public ConfirmedIncidentMemory {
        if (caseId == null || sourceTaskId == null || providerId == null || providerId.isBlank()
                || interfaceId == null || interfaceId.isBlank() || symptom == null || symptom.isBlank()
                || confirmedRootCause == null || confirmedRootCause.isBlank() || approvalId == null
                || approvalId.isBlank() || applicableConditions == null || applicableConditions.isBlank()
                || status == null || version < 1 || createdAt == null) {
            throw new IllegalArgumentException("长期案例记忆字段不完整");
        }
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        if (evidenceRefs.isEmpty()) throw new IllegalArgumentException("长期案例必须保留证据引用");
    }

    /** 案例生命周期。 */
    public enum Status { ACTIVE, EXPIRED, WITHDRAWN }
}
