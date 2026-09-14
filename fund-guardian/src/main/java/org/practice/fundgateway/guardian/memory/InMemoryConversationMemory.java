package org.practice.fundgateway.guardian.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 用于单元测试和无数据库演示的会话记忆适配器。 */
public class InMemoryConversationMemory implements ConversationMemoryPort {

    private final List<MemoryMessage> messages = java.util.Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, MemorySummary> summaries = new ConcurrentHashMap<>();
    private final Clock clock;

    /** 使用系统时钟创建内存记忆。 */
    public InMemoryConversationMemory() {
        this(Clock.systemUTC());
    }

    /** 注入时钟，便于验证过期消息。 */
    public InMemoryConversationMemory(Clock clock) {
        this.clock = clock;
    }

    /** 只返回同一会话和同一诊断任务的最近消息，避免上下文串线。 */
    @Override
    public List<MemoryMessage> loadRecent(String conversationId, UUID diagnosticTaskId, int limit) {
        if (limit < 1) throw new IllegalArgumentException("记忆读取条数必须大于零");
        Instant now = Instant.now(clock);
        synchronized (messages) {
            return messages.stream()
                    .filter(message -> message.conversationId().equals(conversationId)
                            && message.diagnosticTaskId().equals(diagnosticTaskId)
                            && (message.expiresAt() == null || message.expiresAt().isAfter(now)))
                    .sorted(Comparator.comparing(MemoryMessage::createdAt).reversed())
                    .limit(limit)
                    .sorted(Comparator.comparing(MemoryMessage::createdAt))
                    .toList();
        }
    }

    /** 追加消息并拒绝空内容。 */
    @Override
    public void append(MemoryMessage message) {
        messages.add(message);
    }

    /** 按会话和任务保存最新摘要。 */
    @Override
    public void saveSummary(MemorySummary summary) {
        summaries.put(key(summary.conversationId(), summary.diagnosticTaskId()), summary);
    }

    /** 返回当前会话的最新摘要。 */
    @Override
    public MemorySummary loadLatestSummary(String conversationId, UUID diagnosticTaskId) {
        return summaries.get(key(conversationId, diagnosticTaskId));
    }

    /** 组合隔离键，避免不同任务共享摘要。 */
    private String key(String conversationId, UUID diagnosticTaskId) {
        return conversationId + "|" + diagnosticTaskId;
    }
}
