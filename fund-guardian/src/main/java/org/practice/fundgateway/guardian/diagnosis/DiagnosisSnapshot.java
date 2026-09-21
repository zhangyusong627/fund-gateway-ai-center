package org.practice.fundgateway.guardian.diagnosis;

import java.time.Instant;
import java.util.List;

/** 保存一次诊断所使用的只读事实，确保模型输入可以模拟。 */
public record DiagnosisSnapshot(
        String snapshotId,
        Instant capturedAt,
        MetricsEvidence metrics,
        List<RuleFinding> ruleFindings,
        ContractEvidence contract,
        List<RagCitation> ragCitations,
        String riskFingerprint) {

    /** 构造快照并复制集合，避免调用方在诊断过程中修改证据。 */
    public DiagnosisSnapshot {
        if (snapshotId == null || snapshotId.isBlank() || capturedAt == null) {
            throw new IllegalArgumentException("诊断快照标识和采集时间不能为空");
        }
        ruleFindings = ruleFindings == null ? List.of() : List.copyOf(ruleFindings);
        ragCitations = ragCitations == null ? List.of() : List.copyOf(ragCitations);
    }

    /** 表示一条可追溯的 RAG 原文引用。 */
    public record RagCitation(String citationId, String content, String documentId,
                              String documentVersion, String locator) {
    }
}
