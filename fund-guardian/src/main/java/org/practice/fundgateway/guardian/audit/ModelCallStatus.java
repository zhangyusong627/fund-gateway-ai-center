package org.practice.fundgateway.guardian.audit;

/** 表示一次模型调用最终记录的结果状态。 */
public enum ModelCallStatus {
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    REJECTED_BY_GATE
}
