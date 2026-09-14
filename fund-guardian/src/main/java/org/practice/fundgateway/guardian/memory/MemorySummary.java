package org.practice.fundgateway.guardian.memory;

import java.time.Instant;
import java.util.UUID;

/** 表示一组较早消息的可替换摘要，不替代原始诊断证据。 */
public record MemorySummary(String conversationId, UUID diagnosticTaskId, int version,
                            String content, Instant createdAt) {

    /** 校验摘要版本和内容。 */
    public MemorySummary {
        if (conversationId == null || conversationId.isBlank() || diagnosticTaskId == null
                || version < 1 || content == null || content.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("记忆摘要字段不完整");
        }
    }
}
