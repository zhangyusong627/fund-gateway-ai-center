package org.practice.fundgateway.guardian.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.guardian.diagnosis.ContractEvidence;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.MetricsEvidence;

/** 验证模型输入同时包含摘要、最近消息和工具轨迹。 */
class AgentContextPromptBuilderTest {

    @Test
    void shouldRenderAllContextSections() {
        UUID task = UUID.randomUUID();
        AgentContext context = new AgentContext("c-1", task,
                List.of(new MemoryMessage(UUID.randomUUID(), "c-1", task, MemoryMessage.Role.USER,
                        MemoryMessage.MessageType.USER, "之前的问题", Instant.now(), null)),
                new MemorySummary("c-1", task, 1, "之前已确认超时率异常", Instant.now()),
                new DiagnosisSnapshot("s-1", Instant.now(),
                        new MetricsEvidence("synthetic-provider", "credit-apply", 1, 1, 0, 1, 2),
                        List.of(), new ContractEvidence("synthetic-provider", "credit-apply", 20, 3000),
                        List.of(), "f-1"), List.of("querySyntheticContract -> result"));

        String prompt = new AgentContextPromptBuilder().build(context, "当前问题");

        assertTrue(prompt.contains("之前已确认超时率异常"));
        assertTrue(prompt.contains("之前的问题"));
        assertTrue(prompt.contains("querySyntheticContract"));
    }
}
