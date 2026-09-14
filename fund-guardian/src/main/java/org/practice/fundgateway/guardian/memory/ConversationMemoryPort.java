package org.practice.fundgateway.guardian.memory;

import java.util.List;
import java.util.UUID;

/** 定义会话记忆的业务端口，基础设施不能改变会话和任务隔离规则。 */
public interface ConversationMemoryPort {

    /** 按会话、诊断任务读取未过期的最近消息。 */
    List<MemoryMessage> loadRecent(String conversationId, UUID diagnosticTaskId, int limit);

    /** 保存一条会话消息。 */
    void append(MemoryMessage message);

    /** 保存当前会话摘要。 */
    void saveSummary(MemorySummary summary);

    /** 读取最新摘要。 */
    MemorySummary loadLatestSummary(String conversationId, UUID diagnosticTaskId);
}
