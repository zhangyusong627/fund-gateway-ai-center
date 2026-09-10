package org.practice.fundgateway.guardian.diagnosis;

import java.util.List;

/** 保存一个合成故障现象及其预期证据，作为首批诊断评测样例。 */
public record DiagnosticCase(
        String caseId,
        String providerId,
        String interfaceId,
        List<String> symptoms,
        String expectedConclusion,
        List<String> requiredEvidence) {
}
