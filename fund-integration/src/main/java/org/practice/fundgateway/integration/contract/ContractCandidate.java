package org.practice.fundgateway.integration.contract;

import java.time.Instant;
import java.util.List;

/** 表示由模型提出、等待 Java 校验和人工确认的候选接口规范。 */
public record ContractCandidate(
        String candidateId,
        String providerId,
        String interfaceId,
        String purpose,
        String endpoint,
        String httpMethod,
        List<CandidateFieldDefinition> fields,
        List<ContractEvidenceCitation> evidence,
        String sourceDocumentVersion,
        String extractionModel,
        Instant createdAt,
        ContractCandidateStatus status) {

    /** 固定集合字段，防止候选创建后被调用方原地修改。 */
    public ContractCandidate {
        fields = List.copyOf(fields == null ? List.of() : fields);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
