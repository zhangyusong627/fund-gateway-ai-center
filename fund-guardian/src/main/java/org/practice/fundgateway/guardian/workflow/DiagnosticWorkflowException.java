package org.practice.fundgateway.guardian.workflow;

/** 表示诊断工作流违反状态、期限或输入约束。 */
public class DiagnosticWorkflowException extends RuntimeException {

    /** 创建带明确业务原因的工作流异常。 */
    public DiagnosticWorkflowException(String message) {
        super(message);
    }
}
