package org.practice.fundgateway.integration.contract;

/** 描述候选接口规范的一项确定性校验问题。 */
public record ContractValidationIssue(String path, String message) {
}
