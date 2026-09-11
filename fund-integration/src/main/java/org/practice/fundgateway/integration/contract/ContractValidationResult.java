package org.practice.fundgateway.integration.contract;

import java.util.List;

/** 保存候选接口规范三层校验的结果。 */
public record ContractValidationResult(boolean valid, List<ContractValidationIssue> issues) {

    /** 创建通过结果。 */
    public static ContractValidationResult passed() {
        return new ContractValidationResult(true, List.of());
    }

    /** 创建失败结果。 */
    public static ContractValidationResult failed(List<ContractValidationIssue> issues) {
        return new ContractValidationResult(false, List.copyOf(issues));
    }
}
