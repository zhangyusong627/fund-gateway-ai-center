package org.practice.fundgateway.guardian.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 验证记忆读取的任务隔离、数量限制和摘要恢复。 */
class InMemoryConversationMemoryTest {

    @Test
    void shouldKeepConversationAndTaskIsolated() {
        InMemoryConversationMemory memory = new InMemoryConversationMemory();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        memory.append(message("c-1", taskA, "A"));
        memory.append(message("c-1", taskB, "B"));

        assertEquals("A", memory.loadRecent("c-1", taskA, 10).getFirst().content());
        assertEquals("B", memory.loadRecent("c-1", taskB, 10).getFirst().content());
    }

    @Test
    void shouldRejectInvalidLimit() {
        InMemoryConversationMemory memory = new InMemoryConversationMemory();
        assertThrows(IllegalArgumentException.class,
                () -> memory.loadRecent("c-1", UUID.randomUUID(), 0));
    }

    @Test
    void shouldRestoreLatestSummary() {
        InMemoryConversationMemory memory = new InMemoryConversationMemory();
        UUID task = UUID.randomUUID();
        memory.saveSummary(new MemorySummary("c-1", task, 1, "已确认 QPS 异常", Instant.now()));

        assertEquals("已确认 QPS 异常", memory.loadLatestSummary("c-1", task).content());
    }

    private MemoryMessage message(String conversationId, UUID taskId, String content) {
        return new MemoryMessage(UUID.randomUUID(), conversationId, taskId,
                MemoryMessage.Role.USER, MemoryMessage.MessageType.USER, content, Instant.now(), null);
    }
}
