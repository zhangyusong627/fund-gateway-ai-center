package org.practice.fundgateway.guardian.ai;

/** 表示模型基础设施失败，并明确该失败是否允许有限重试。 */
public class ModelGatewayException extends RuntimeException {

    private final boolean retryable;

    /** 创建带重试策略的模型基础设施异常。 */
    public ModelGatewayException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** 返回该异常是否可以触发一次剩余预算内的重试。 */
    public boolean retryable() {
        return retryable;
    }
}
