package org.practice.fundgateway.guardian.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 在上下文过长时保留最近消息，并生成不替代原始证据的确定性摘要。 */
public class ConversationMemoryCompactor {

    private final ConversationMemoryPort memory;
    private final Clock clock;
    private final int maxCharacters;

    /** 创建固定字符预算的记忆裁剪器。 */
    public ConversationMemoryCompactor(ConversationMemoryPort memory, int maxCharacters) {
        this(memory, maxCharacters, Clock.systemUTC());
    }

    /** 注入时钟以便测试摘要版本。 */
    public ConversationMemoryCompactor(ConversationMemoryPort memory, int maxCharacters, Clock clock) {
        if (memory == null || maxCharacters < 1 || clock == null) {
            throw new IllegalArgumentException("记忆裁剪配置无效");
        }
        this.memory = memory;
        this.maxCharacters = maxCharacters;
        this.clock = clock;
    }

    /** 超过预算时将较早消息压缩为摘要，并保留最近窗口供下一轮使用。 */
    public MemorySummary compact(String conversationId, UUID diagnosticTaskId, int recentLimit) {
        List<MemoryMessage> messages = memory.loadRecent(conversationId, diagnosticTaskId, Integer.MAX_VALUE);
        int characters = messages.stream().mapToInt(message -> message.content().length()).sum();
        if (characters <= maxCharacters) return memory.loadLatestSummary(conversationId, diagnosticTaskId);
        int keepFrom = Math.max(0, messages.size() - Math.max(1, recentLimit));
        String content = messages.subList(0, keepFrom).stream()
                .map(message -> message.role() + ":" + message.content())
                .reduce((left, right) -> left + "\n" + right).orElse("无早期消息");
        MemorySummary previous = memory.loadLatestSummary(conversationId, diagnosticTaskId);
        int version = previous == null ? 1 : previous.version() + 1;
        MemorySummary summary = new MemorySummary(conversationId, diagnosticTaskId, version,
                "以下内容是会话语境摘要，不替代原始诊断证据：\n" + content, Instant.now(clock));
        memory.saveSummary(summary);
        return summary;
    }
}
