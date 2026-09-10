package org.practice.fundgateway.experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.practice.fundgateway.guardian.tool.SyntheticContractQueryTool;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.deepseek.api.DeepSeekApi.FunctionTool;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** D3 真实模型联调：让 DeepSeek 请求只读契约工具，再回灌工具结果。 */
@Component
@Profile("d3-real")
public class DeepSeekToolCallingExperiment implements CommandLineRunner {

    private static final String MODEL = "deepseek-v4-flash";
    private static final String BASE_URL = "https://api.deepseek.com";
    private static final Path EVIDENCE_ROOT = Path.of("..", "docs", "learning");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SyntheticContractQueryTool tool = new SyntheticContractQueryTool();

    @Override
    public void run(String... args) {
        try {
            executeOnce();
        } catch (Exception exception) {
            throw new IllegalStateException("D3 real call failed: " + exception.getClass().getSimpleName());
        }
    }

    /** 执行首轮工具选择和第二轮结果回灌。 */
    private void executeOnce() throws IOException {
        String apiKey = requireEnv("DEEPSEEK_API_KEY");
        String prompt = "查询 synthetic-provider 的 credit-apply 接口 QPS 限制和超时时间，必须先调用工具，不要编造事实。";
        Path evidence = EVIDENCE_ROOT.resolve("D3-real-call-" + System.currentTimeMillis());
        Files.createDirectories(evidence);
        DeepSeekApi api = DeepSeekApi.builder().baseUrl(BASE_URL).apiKey(apiKey).build();
        FunctionTool.Function function = new FunctionTool.Function(tool.getToolDefinition().description(),
                tool.getToolDefinition().name(), tool.getToolDefinition().inputSchema());
        FunctionTool functionTool = new FunctionTool(function);
        ChatCompletionRequest firstRequest = ChatCompletionRequest.builder()
                .model(MODEL).messages(List.of(new ChatCompletionMessage(prompt, Role.USER)))
                .tools(List.of(functionTool)).stream(false).build();
        save(evidence.resolve("request-1.json"), mapper.writeValueAsString(firstRequest));
        var firstResponse = api.chatCompletionEntity(firstRequest);
        save(evidence.resolve("response-1.json"), mapper.writeValueAsString(firstResponse.getBody()));
        ChatCompletionMessage assistant = firstResponse.getBody().choices().getFirst().message();
        ToolCall call = assistant.toolCalls().getFirst();
        String result = tool.call(call.function().arguments());
        save(evidence.resolve("tool-result.json"), result);
        ChatCompletionMessage toolMessage = new ChatCompletionMessage(result, Role.TOOL, null, call.id(), null);
        ChatCompletionRequest secondRequest = ChatCompletionRequest.builder().model(MODEL)
                .messages(List.of(new ChatCompletionMessage(prompt, Role.USER), assistant, toolMessage))
                .stream(false).build();
        save(evidence.resolve("request-2.json"), mapper.writeValueAsString(secondRequest));
        var secondResponse = api.chatCompletionEntity(secondRequest);
        save(evidence.resolve("response-2.json"), mapper.writeValueAsString(secondResponse.getBody()));
        System.out.println("D3 real tool call completed; evidence=" + evidence);
    }

    /** 读取密钥但不在异常和证据中输出密钥。 */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }

    /** 以 UTF-8 保存原始请求或响应。 */
    private static void save(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }
}
