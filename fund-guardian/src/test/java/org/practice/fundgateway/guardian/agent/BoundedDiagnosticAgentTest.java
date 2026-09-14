package org.practice.fundgateway.guardian.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.guardian.diagnosis.ContractEvidence;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.MetricsEvidence;
import org.practice.fundgateway.guardian.memory.AgentContext;
import org.practice.fundgateway.guardian.memory.InMemoryConversationMemory;
import org.practice.fundgateway.guardian.tool.DiagnosticToolExecutor;
import org.practice.fundgateway.guardian.tool.SyntheticDiagnosticToolRegistry;

/** 验证 Agent 的工具回灌、最终停止和固定轮次预算。 */
class BoundedDiagnosticAgentTest {

    @Test
    void shouldReinjectToolResultAndFinish() {
        InMemoryConversationMemory memory = new InMemoryConversationMemory();
        AtomicInteger decisions = new AtomicInteger();
        AgentDecisionProvider provider = context -> decisions.getAndIncrement() == 0
                ? AgentDecision.tool("querySyntheticContract", "{\"provider\":\"synthetic-provider\",\"interface\":\"credit-apply\"}")
                : AgentDecision.finalAnswer("已获得契约证据");
        AgentContext initialContext = context();
        BoundedDiagnosticAgent agent = new BoundedDiagnosticAgent(provider,
                new DiagnosticToolExecutor(new SyntheticDiagnosticToolRegistry(), 3), memory, 3);

        BoundedDiagnosticAgent.AgentRun run = agent.run(initialContext, "查询 QPS 限制");

        assertEquals("FINAL_ANSWER", run.status());
        assertEquals(2, run.turns());
        assertTrue(run.toolTrace().getFirst().contains("qpsLimit"));
        assertEquals(3, memory.loadRecent("c-1", initialContext.diagnosticTaskId(), 10).size());
    }

    @Test
    void shouldStopWhenTurnBudgetIsExceeded() {
        AgentDecisionProvider provider = context -> AgentDecision.tool("querySyntheticContract",
                "{\"provider\":\"synthetic-provider\",\"interface\":\"credit-apply\"}");
        InMemoryConversationMemory memory = new InMemoryConversationMemory();
        BoundedDiagnosticAgent agent = new BoundedDiagnosticAgent(provider,
                new DiagnosticToolExecutor(new SyntheticDiagnosticToolRegistry(), 3), memory, 2);

        assertEquals("BUDGET_EXCEEDED", agent.run(context(), "继续调查").status());
    }

    private AgentContext context() {
        UUID taskId = UUID.randomUUID();
        return new AgentContext("c-1", taskId, java.util.List.of(), null,
                new DiagnosisSnapshot("snapshot-1", Instant.now(),
                        new MetricsEvidence("synthetic-provider", "credit-apply", 10, 100, 0, 1, 10),
                        java.util.List.of(), new ContractEvidence("synthetic-provider", "credit-apply", 20, 3000),
                        java.util.List.of(), "fingerprint-1"), java.util.List.of());
    }
}
