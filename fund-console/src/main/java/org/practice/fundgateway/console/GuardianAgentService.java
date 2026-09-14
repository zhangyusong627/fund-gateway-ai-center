package org.practice.fundgateway.console;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.guardian.memory.AgentContext;
import org.practice.fundgateway.guardian.memory.AgentContextAssembler;
import org.practice.fundgateway.guardian.memory.AgentContextPromptBuilder;
import org.practice.fundgateway.guardian.memory.ConversationMemoryCompactor;
import org.practice.fundgateway.guardian.memory.ConversationMemoryPort;
import org.practice.fundgateway.guardian.memory.MemoryMessage;
import org.practice.fundgateway.guardian.agent.AgentExecutionState;
import org.practice.fundgateway.guardian.agent.AgentExecutionStatePort;
import org.practice.fundgateway.guardian.diagnosis.ContractEvidence;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.MetricsEvidence;
import org.practice.fundgateway.guardian.tool.DiagnosticToolExecutor;
import org.practice.fundgateway.guardian.tool.SyntheticDiagnosticToolRegistry;
import org.springframework.stereotype.Service;

import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.deepseek.api.DeepSeekApi.FunctionTool;
import tools.jackson.databind.json.JsonMapper;

/** 为控制台提供真实 DeepSeek 两轮工具调查和会话记忆保存。 */
@Service
public class GuardianAgentService {

    private static final String MODEL = "deepseek-v4-flash";
    private static final String TOOL_NAME = "querySyntheticContract";
    private final ConversationMemoryPort memory;
    private final SyntheticDiagnosticToolRegistry registry;
    private final AgentExecutionStatePort executionState;
    private final AgentContextPromptBuilder contextPromptBuilder = new AgentContextPromptBuilder();
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 注入持久化会话记忆。 */
    public GuardianAgentService(ConversationMemoryPort memory, AgentExecutionStatePort executionState) {
        this.memory = memory;
        this.executionState = executionState;
        this.registry = new SyntheticDiagnosticToolRegistry();
    }

    /** 查询指定会话和诊断任务的最近一次 Agent 执行状态。 */
    public Optional<AgentExecutionState> executionState(String conversationId, UUID diagnosticTaskId) {
        if (conversationId == null || conversationId.isBlank() || diagnosticTaskId == null) {
            throw new IllegalArgumentException("执行状态查询字段不完整");
        }
        return executionState.find(conversationId, diagnosticTaskId);
    }

    /** 对未完成的只读调查执行安全重放，使用最近一次用户问题恢复业务意图。 */
    public AgentReplayResponse resume(String conversationId, UUID diagnosticTaskId) throws Exception {
        AgentExecutionState state = executionState(conversationId, diagnosticTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 执行状态不存在"));
        if (state.status() == AgentExecutionState.Status.COMPLETED) {
            throw new IllegalStateException("已完成的 Agent 不需要恢复");
        }
        String prompt = memory.loadRecent(conversationId, diagnosticTaskId, 50).stream()
                .filter(message -> message.role() == MemoryMessage.Role.USER)
                .reduce((first, second) -> second)
                .map(MemoryMessage::content)
                .orElseThrow(() -> new IllegalStateException("恢复所需的用户消息不存在"));
        return replay(conversationId, diagnosticTaskId, prompt);
    }

    /** 执行固定两轮 Agent：模型选择工具，Java 执行后回灌结果。 */
    public AgentReplayResponse replay(String conversationId, UUID diagnosticTaskId, String prompt) throws Exception {
        if (conversationId == null || conversationId.isBlank() || diagnosticTaskId == null
                || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Agent 请求字段不完整");
        }
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) return new AgentReplayResponse("MODEL_UNAVAILABLE", 0, List.of(), null);
        UUID executionId = UUID.randomUUID();
        executionState.save(new AgentExecutionState(executionId, conversationId, diagnosticTaskId,
                AgentExecutionState.Status.RUNNING, 0, 0, null, Instant.now()));
        memory.append(new MemoryMessage(UUID.randomUUID(), conversationId, diagnosticTaskId,
                MemoryMessage.Role.USER, MemoryMessage.MessageType.USER, prompt, Instant.now(), null));
        new ConversationMemoryCompactor(memory, 4000).compact(conversationId, diagnosticTaskId, 12);
        AgentContext context = new AgentContextAssembler(memory, 12).assemble(conversationId, diagnosticTaskId,
                syntheticSnapshot());
        String contextPrompt = contextPromptBuilder.build(context, prompt);
        DeepSeekApi api = DeepSeekApi.builder().baseUrl("https://api.deepseek.com").apiKey(key).build();
        var tool = registry.require(TOOL_NAME);
        FunctionTool.Function function = new FunctionTool.Function(tool.getToolDefinition().description(),
                tool.getToolDefinition().name(), tool.getToolDefinition().inputSchema());
        FunctionTool functionTool = new FunctionTool(function);
        ChatCompletionRequest firstRequest = ChatCompletionRequest.builder().model(MODEL)
                .messages(List.of(new ChatCompletionMessage(contextPrompt, Role.USER)))
                .tools(List.of(functionTool)).stream(false).build();
        var firstResponse = api.chatCompletionEntity(firstRequest).getBody();
        if (firstResponse == null || firstResponse.choices().isEmpty()) {
            executionState.save(new AgentExecutionState(executionId, conversationId, diagnosticTaskId,
                    AgentExecutionState.Status.FAILED, 1, 0, "empty first response", Instant.now()));
            return new AgentReplayResponse("FAILED", 1, context.toolTrace(), null);
        }
        ChatCompletionMessage assistant = firstResponse.choices().getFirst().message();
        List<ToolCall> calls = assistant.toolCalls();
        if (calls == null || calls.isEmpty()) {
            String content = assistant.content() == null ? "" : assistant.content();
            saveAssistant(conversationId, diagnosticTaskId, content);
            executionState.save(new AgentExecutionState(executionId, conversationId, diagnosticTaskId,
                    AgentExecutionState.Status.COMPLETED, 1, 0, null, Instant.now()));
            return new AgentReplayResponse("FINAL_ANSWER", 1, context.toolTrace(), content);
        }
        ToolCall call = calls.getFirst();
        executionState.save(new AgentExecutionState(executionId, conversationId, diagnosticTaskId,
                AgentExecutionState.Status.WAITING_TOOL, 1, 0, null, Instant.now()));
        DiagnosticToolExecutor.Invocation invocation = new DiagnosticToolExecutor(registry, 1)
                .startInvocation(PermissionContext.syntheticConsole(), "agent-" + conversationId);
        String result;
        try {
            result = invocation.execute(call.function().name(), call.function().arguments());
        } catch (RuntimeException exception) {
            executionState.save(new AgentExecutionState(executionId, conversationId, diagnosticTaskId,
                    AgentExecutionState.Status.FAILED, 1, 0, exception.getClass().getSimpleName(), Instant.now()));
            throw exception;
        }
        executionState.save(new AgentExecutionState(executionId, conversationId, diagnosticTaskId,
                AgentExecutionState.Status.RUNNING, 1, 1, null, Instant.now()));
        memory.append(new MemoryMessage(UUID.randomUUID(), conversationId, diagnosticTaskId,
                MemoryMessage.Role.TOOL, MemoryMessage.MessageType.TOOL_RESULT, result, Instant.now(), null));
        ChatCompletionMessage toolMessage = new ChatCompletionMessage(result, Role.TOOL, null, call.id(), null);
        AgentContext afterToolContext = new AgentContext(context.conversationId(), context.diagnosticTaskId(),
                context.recentMessages(), context.summary(), context.diagnosisSnapshot(),
                List.of(TOOL_NAME + " -> " + result));
        String secondPrompt = contextPromptBuilder.build(afterToolContext, prompt);
        ChatCompletionRequest secondRequest = ChatCompletionRequest.builder().model(MODEL)
                .messages(List.of(new ChatCompletionMessage(secondPrompt, Role.USER), assistant, toolMessage))
                .stream(false).build();
        var secondResponse = api.chatCompletionEntity(secondRequest).getBody();
        String content = secondResponse == null || secondResponse.choices().isEmpty()
                ? null : secondResponse.choices().getFirst().message().content();
        if (content != null && !content.isBlank()) saveAssistant(conversationId, diagnosticTaskId, content);
        executionState.save(new AgentExecutionState(executionId, conversationId, diagnosticTaskId,
                content == null ? AgentExecutionState.Status.FAILED : AgentExecutionState.Status.COMPLETED,
                2, 1, content == null ? "empty final response" : null, Instant.now()));
        return new AgentReplayResponse(content == null ? "FAILED" : "FINAL_ANSWER", 2,
                List.of(TOOL_NAME + " -> " + result), content);
    }

    /** 保存模型最终消息。 */
    private void saveAssistant(String conversationId, UUID taskId, String content) {
        memory.append(new MemoryMessage(UUID.randomUUID(), conversationId, taskId,
                MemoryMessage.Role.ASSISTANT, MemoryMessage.MessageType.ASSISTANT, content, Instant.now(), null));
    }

    /** 生成本地 Agent 上下文使用的合成诊断事实。 */
    private DiagnosisSnapshot syntheticSnapshot() {
        return new DiagnosisSnapshot("agent-console-snapshot", Instant.now(),
                new MetricsEvidence("synthetic-provider", "credit-apply", 95, 820, 0.18, 48, 50),
                List.of(), new ContractEvidence("synthetic-provider", "credit-apply", 20, 3000),
                List.of(), "agent-console-fingerprint");
    }

    /** 控制台 Agent 回放结果。 */
    public record AgentReplayResponse(String status, int turns, List<String> toolTrace, String finalContent) {
        /** 固定工具轨迹结构。 */
        public AgentReplayResponse {
            toolTrace = List.copyOf(toolTrace == null ? List.of() : toolTrace);
        }
    }
}
