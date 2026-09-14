package org.practice.fundgateway.guardian.memory;

import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;

/** 表示一次 Agent 调查可见的结构化上下文，区分会话记忆和诊断事实。 */
public record AgentContext(String conversationId, UUID diagnosticTaskId,
                           List<MemoryMessage> recentMessages, MemorySummary summary,
                           DiagnosisSnapshot diagnosisSnapshot, List<String> toolTrace) {

    /** 复制集合，避免调用方在模型调用期间修改上下文。 */
    public AgentContext {
        if (conversationId == null || conversationId.isBlank() || diagnosticTaskId == null
                || diagnosisSnapshot == null) {
            throw new IllegalArgumentException("Agent 上下文字段不完整");
        }
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        toolTrace = toolTrace == null ? List.of() : List.copyOf(toolTrace);
    }
}
