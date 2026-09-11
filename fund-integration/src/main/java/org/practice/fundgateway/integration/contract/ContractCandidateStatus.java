package org.practice.fundgateway.integration.contract;

/** 表示候选接口规范在人工确认前后的状态。 */
public enum ContractCandidateStatus {
    DRAFT,
    PENDING_REVIEW,
    RETURNED,
    REJECTED,
    APPROVED
}
