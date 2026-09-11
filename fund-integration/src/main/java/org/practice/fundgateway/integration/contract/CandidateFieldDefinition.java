package org.practice.fundgateway.integration.contract;

/** 表示候选接口规范中的一个字段事实。 */
public record CandidateFieldDefinition(
        String name,
        String type,
        FieldRequirement requirement,
        String condition,
        String description,
        ContractEvidenceCitation evidence) {
}
