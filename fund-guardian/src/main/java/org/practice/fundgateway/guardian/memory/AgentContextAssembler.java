package org.practice.fundgateway.guardian.memory;

import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;

/** 从记忆端口读取有限历史并组装当前 Agent 上下文。 */
public class AgentContextAssembler {

    private final ConversationMemoryPort memory;
    private final int recentMessageLimit;

    /** 创建固定最近消息窗口的上下文组装器。 */
    public AgentContextAssembler(ConversationMemoryPort memory, int recentMessageLimit) {
        if (memory == null || recentMessageLimit < 1) {
            throw new IllegalArgumentException("Agent 上下文配置无效");
        }
        this.memory = memory;
        this.recentMessageLimit = recentMessageLimit;
    }

    /** 读取当前会话和诊断任务的记忆，绝不跨任务拼接消息。 */
    public AgentContext assemble(String conversationId, UUID diagnosticTaskId,
                                 DiagnosisSnapshot snapshot) {
        return new AgentContext(conversationId, diagnosticTaskId,
                memory.loadRecent(conversationId, diagnosticTaskId, recentMessageLimit),
                memory.loadLatestSummary(conversationId, diagnosticTaskId), snapshot, java.util.List.of());
    }
}
