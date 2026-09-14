package org.practice.fundgateway.guardian.agent;

import java.time.Instant;
import java.util.UUID;

/** 表示可恢复的 Agent 执行游标，不保存模型密钥或认证信息。 */
public record AgentExecutionState(UUID executionId, String conversationId, UUID diagnosticTaskId,
                                  Status status, int turn, int toolCalls, String lastError, Instant updatedAt) {

    /** 校验执行游标的计数和业务边界。 */
    public AgentExecutionState {
        if (executionId == null || conversationId == null || conversationId.isBlank()
                || diagnosticTaskId == null || status == null || turn < 0 || toolCalls < 0 || updatedAt == null) {
            throw new IllegalArgumentException("Agent 执行状态字段不完整");
        }
    }

    /** Agent 执行生命周期。 */
    public enum Status { RUNNING, WAITING_TOOL, COMPLETED, FAILED, INTERRUPTED }
}
