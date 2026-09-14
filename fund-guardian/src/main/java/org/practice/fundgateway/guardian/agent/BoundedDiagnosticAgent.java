package org.practice.fundgateway.guardian.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.guardian.memory.AgentContext;
import org.practice.fundgateway.guardian.memory.ConversationMemoryPort;
import org.practice.fundgateway.guardian.memory.MemoryMessage;
import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.guardian.tool.DiagnosticToolExecutor;

/** 编排有界的诊断 Agent 循环，Java 控制工具权限、预算和终止条件。 */
public class BoundedDiagnosticAgent {

    private final AgentDecisionProvider decisionProvider;
    private final DiagnosticToolExecutor toolExecutor;
    private final ConversationMemoryPort memory;
    private final int maxTurns;

    /** 创建最多执行固定轮次的 Agent。 */
    public BoundedDiagnosticAgent(AgentDecisionProvider decisionProvider,
                                 DiagnosticToolExecutor toolExecutor,
                                 ConversationMemoryPort memory, int maxTurns) {
        if (decisionProvider == null || toolExecutor == null || memory == null || maxTurns < 1) {
            throw new IllegalArgumentException("Agent 配置无效");
        }
        this.decisionProvider = decisionProvider;
        this.toolExecutor = toolExecutor;
        this.memory = memory;
        this.maxTurns = maxTurns;
    }

    /** 执行模型决策、工具回灌和最终停止的有限循环。 */
    public AgentRun run(AgentContext initialContext, String userMessage) {
        return run(initialContext, userMessage, PermissionContext.syntheticDiagnostic(),
                "agent-" + initialContext.conversationId());
    }

    /** 使用请求专属权限和审计标识执行 Agent，避免工具调用共享默认身份。 */
    public AgentRun run(AgentContext initialContext, String userMessage,
                        PermissionContext permissionContext, String traceId) {
        if (permissionContext == null || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Agent 权限上下文和 traceId 不能为空");
        }
        memory.append(new MemoryMessage(UUID.randomUUID(), initialContext.conversationId(),
                initialContext.diagnosticTaskId(), MemoryMessage.Role.USER,
                MemoryMessage.MessageType.USER, userMessage, Instant.now(), null));
        AgentContext context = initialContext;
        List<String> trace = new ArrayList<>();
        DiagnosticToolExecutor.Invocation invocation = toolExecutor.startInvocation(permissionContext, traceId);
        for (int turn = 1; turn <= maxTurns; turn++) {
            AgentDecision decision = decisionProvider.decide(context);
            if (decision.kind() == AgentDecision.Kind.FINAL) {
                memory.append(new MemoryMessage(UUID.randomUUID(), context.conversationId(),
                        context.diagnosticTaskId(), MemoryMessage.Role.ASSISTANT,
                        MemoryMessage.MessageType.ASSISTANT, decision.finalContent(), Instant.now(), null));
                return new AgentRun("FINAL_ANSWER", turn, trace, decision.finalContent());
            }
            try {
                String result = invocation.execute(decision.toolName(), decision.toolInput());
                trace.add(decision.toolName() + " -> " + result);
                memory.append(new MemoryMessage(UUID.randomUUID(), context.conversationId(),
                        context.diagnosticTaskId(), MemoryMessage.Role.TOOL,
                        MemoryMessage.MessageType.TOOL_RESULT, result, Instant.now(), null));
                context = new AgentContext(context.conversationId(), context.diagnosticTaskId(),
                        context.recentMessages(), context.summary(), context.diagnosisSnapshot(), trace);
            } catch (RuntimeException exception) {
                return new AgentRun("TOOL_DENIED", turn, trace, null);
            }
        }
        return new AgentRun("BUDGET_EXCEEDED", maxTurns, trace, null);
    }

    /** 封装 Agent 终止状态和可审计的工具轨迹。 */
    public record AgentRun(String status, int turns, List<String> toolTrace, String finalContent) {
        /** 固定轨迹集合，避免调用方修改审计结果。 */
        public AgentRun {
            toolTrace = List.copyOf(toolTrace);
        }
    }
}
