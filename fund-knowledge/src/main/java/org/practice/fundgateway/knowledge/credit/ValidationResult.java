package org.practice.fundgateway.knowledge.credit;

import java.util.List;

/** 保存结构校验或业务校验的确定性结果。 */
public record ValidationResult(boolean valid, List<ValidationIssue> issues) {

    /** 创建通过结果。 */
    public static ValidationResult passed() {
        return new ValidationResult(true, List.of());
    }

    /** 创建失败结果并固定问题列表，避免调用方修改结果。 */
    public static ValidationResult failed(List<ValidationIssue> issues) {
        return new ValidationResult(false, List.copyOf(issues));
    }
}
