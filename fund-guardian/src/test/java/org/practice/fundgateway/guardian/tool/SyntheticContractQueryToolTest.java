package org.practice.fundgateway.guardian.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;

/** 验证 ToolCallingManager 的工具注册、执行和调用预算。 */
class SyntheticContractQueryToolTest {

    private final SyntheticContractQueryTool tool = new SyntheticContractQueryTool();

    /** 当前请求绑定的工具可以被管理器解析并执行。 */
    @Test
    void registeredToolCanBeExecuted() {
        Prompt prompt = promptWithCalls(1, 1);
        DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
                .maxCallsPerTool("querySyntheticContract", 2)
                .build();

        ToolExecutionResult result = manager.executeToolCalls(prompt, toolCallResponse(1));

        assertTrue(result.conversationHistory().toString().contains("qpsLimit"));
        assertEquals("querySyntheticContract", tool.getToolDefinition().name());
    }

    /** 未注册工具不能被当前请求执行。 */
    @Test
    void unregisteredToolIsRejected() {
        Prompt prompt = promptWithCalls(1, 1);
        assertThrows(IllegalStateException.class, () ->
                DefaultToolCallingManager.builder().build().executeToolCalls(prompt, unknownToolResponse()));
    }

    /** 超过单工具预算时由 Spring AI 抛出明确的预算异常。 */
    @Test
    void toolCallLimitIsEnforced() {
        Prompt prompt = promptWithCalls(2, 1);
        DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
                .maxCallsPerTool("querySyntheticContract", 1)
                .build();

        assertThrows(ToolCallLimitExceededException.class,
                () -> manager.executeToolCalls(prompt, toolCallResponse(2)));
    }

    /** 工具参数不符合约束时，当前默认管理器直接向上抛出异常。 */
    @Test
    void toolFailureBecomesToolResponse() {
        Prompt prompt = promptWithCalls(1, 1);
        DefaultToolCallingManager manager = DefaultToolCallingManager.builder().build();
        assertThrows(IllegalArgumentException.class,
                () -> manager.executeToolCalls(prompt, invalidInputResponse()));
    }

    /** 参数值必须精确匹配，不能靠 JSON 文本中的部分字符串通过校验。 */
    @Test
    void partialInputMatchIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.call("{\"provider\":\"not-synthetic-provider\",\"interface\":\"credit-apply\"}"));
    }

    /** 构造带工具回调列表的提示。 */
    private Prompt promptWithCalls(int count, int limit) {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(List.of(tool))
                .build();
        return new Prompt("查询合成授信契约", options);
    }

    /** 构造模型请求一个已注册工具的响应。 */
    private ChatResponse toolCallResponse(int count) {
        List<AssistantMessage.ToolCall> calls = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new AssistantMessage.ToolCall(
                        "call-" + index, "function", "querySyntheticContract",
                        "{\"provider\":\"synthetic-provider\",\"interface\":\"credit-apply\"}"))
                .toList();
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .toolCalls(calls).build())));
    }

    /** 构造模型请求未注册工具的响应。 */
    private ChatResponse unknownToolResponse() {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "unknown-1", "function", "unknownTool", "{}"))).build())));
    }

    /** 构造模型请求已注册工具但传入非法参数的响应。 */
    private ChatResponse invalidInputResponse() {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "invalid-1", "function", "querySyntheticContract", "{}"))).build())));
    }
}
