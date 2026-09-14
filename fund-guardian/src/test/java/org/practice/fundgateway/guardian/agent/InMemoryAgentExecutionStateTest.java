package org.practice.fundgateway.guardian.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 验证 Agent 执行游标可以按会话和任务恢复。 */
class InMemoryAgentExecutionStateTest {
    @Test
    void shouldRestoreLatestState() {
        InMemoryAgentExecutionState store = new InMemoryAgentExecutionState();
        UUID task = UUID.randomUUID();
        store.save(new AgentExecutionState(UUID.randomUUID(), "c-1", task,
                AgentExecutionState.Status.INTERRUPTED, 1, 1, "timeout", Instant.now()));
        assertEquals(AgentExecutionState.Status.INTERRUPTED, store.find("c-1", task).orElseThrow().status());
    }
}
