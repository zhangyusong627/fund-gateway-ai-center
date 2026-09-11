package org.practice.fundgateway.integration.contract;

/** 返回审核操作后的候选状态和可选发布版本。 */
public record ContractReviewResult(
        ContractCandidate candidate,
        PublishedContractVersion publishedVersion,
        String message) {
}
