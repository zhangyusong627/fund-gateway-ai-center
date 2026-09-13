package org.practice.fundgateway.guardian.metrics;

/** 表示处理阶段失败，交由 Kafka 消费者错误处理器决定重投。 */
public class MetricEventProcessingException extends RuntimeException {

    /** 创建带失败记录 ID 的处理异常。 */
    public MetricEventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
