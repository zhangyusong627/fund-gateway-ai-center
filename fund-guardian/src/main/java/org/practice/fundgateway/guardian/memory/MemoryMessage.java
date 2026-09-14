package org.practice.fundgateway.guardian.memory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 表示绑定诊断任务的一条可审计会话记忆。 */
public record MemoryMessage(UUID messageId, String conversationId, UUID diagnosticTaskId,
                            Role role, MessageType messageType, String content,
                            Instant createdAt, Instant expiresAt) {

    /** 校验消息具有稳定的会话和任务边界。 */
    public MemoryMessage {
        Objects.requireNonNull(messageId, "messageId");
        if (conversationId == null || conversationId.isBlank() || diagnosticTaskId == null
                || role == null || messageType == null || content == null || content.isBlank()
                || createdAt == null) {
            throw new IllegalArgumentException("记忆消息字段不完整");
        }
    }

    /** 会话消息角色。 */
    public enum Role { USER, ASSISTANT, TOOL, SYSTEM }

    /** 会话消息类型，区分普通对话和工具轨迹。 */
    public enum MessageType { USER, ASSISTANT, TOOL_RESULT, SYSTEM, SUMMARY }
}
