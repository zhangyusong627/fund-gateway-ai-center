package org.practice.fundgateway.guardian.memory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 验证超长会话会生成可版本化摘要。 */
class ConversationMemoryCompactorTest {

    @Test
    void shouldCreateSummaryWhenCharacterBudgetIsExceeded() {
        InMemoryConversationMemory memory = new InMemoryConversationMemory();
        UUID task = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            memory.append(new MemoryMessage(UUID.randomUUID(), "c-1", task, MemoryMessage.Role.USER,
                    MemoryMessage.MessageType.USER, "历史消息-" + i + "-内容", Instant.now().plusSeconds(i), null));
        }

        MemorySummary summary = new ConversationMemoryCompactor(memory, 5).compact("c-1", task, 1);

        assertNotNull(summary);
        assertTrue(summary.content().contains("历史消息"));
    }
}
