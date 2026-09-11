package org.practice.fundgateway.integration.contract;

import java.time.Instant;
import java.util.List;

/** 保存人工确认后发布的不可变接口规范版本。 */
public record PublishedContractVersion(
        String providerId,
        String interfaceId,
        String version,
        String sourceDocumentVersion,
        String endpoint,
        String httpMethod,
        List<CandidateFieldDefinition> fields,
        List<ContractEvidenceCitation> evidence,
        String reviewer,
        Instant effectiveAt) {

    /** 固定发布版本中的集合，防止发布后被外部修改。 */
    public PublishedContractVersion {
        fields = List.copyOf(fields);
        evidence = List.copyOf(evidence);
    }
}
