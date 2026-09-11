package org.practice.fundgateway.guardian.workflow;

/** 表示诊断任务从创建到模拟治理完成的生命周期状态。 */
public enum DiagnosticTaskStatus {
    DIAGNOSED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    RETURNED,
    EXPIRED,
    SIMULATED
}
