package org.practice.fundgateway.console;

import org.practice.fundgateway.guardian.ai.ModelGateway;
import org.practice.fundgateway.guardian.ai.ModelGatewayException;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.json.JsonMapper;

/** 把 DeepSeek SDK 适配为 Guardian 的模型出站端口，不向业务层泄漏 SDK 类型。 */
public class DeepSeekModelGateway implements ModelGateway {

    private final DeepSeekApi api;
    private final JsonMapper mapper;

    /** 只从进程环境变量读取密钥；缺失时保持不可用而不发起空密钥请求。 */
    public DeepSeekModelGateway(String apiKey, JsonMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("DeepSeek JSON 映射器不能为空");
        }
        this.api = apiKey == null || apiKey.isBlank() ? null
                : DeepSeekApi.builder().baseUrl("https://api.deepseek.com").apiKey(apiKey).build();
        this.mapper = mapper;
    }

    /** 返回 DeepSeek 是否具备调用所需密钥。 */
    @Override
    public boolean available() {
        return api != null;
    }

    /** 发起一次固定模型、非流式、结构化输出请求并保留原始响应。 */
    @Override
    public ModelCompletion complete(ModelRequest request) {
        if (!available()) {
            throw new ModelGatewayException("DeepSeek 适配器不可用", null, false);
        }
        ChatCompletionRequest apiRequest = ChatCompletionRequest.builder()
                .model(request.model())
                .messages(java.util.List.of(new ChatCompletionMessage(request.prompt(), ChatCompletionMessage.Role.USER)))
                .stream(false)
                .maxTokens(request.maxOutputTokens())
                .thinking(ChatCompletionRequest.Thinking.DISABLED)
                .build();
        try {
            var entity = api.chatCompletionEntity(apiRequest);
            var response = entity == null ? null : entity.getBody();
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().getFirst().message().content() == null) {
                throw new IllegalStateException("DeepSeek 响应缺少结构化内容");
            }
            return new ModelCompletion(response.choices().getFirst().message().content(),
                    mapper.writeValueAsString(response));
        } catch (Exception exception) {
            if (exception instanceof ModelGatewayException gatewayException) {
                throw gatewayException;
            }
            throw new ModelGatewayException("DeepSeek 调用失败", exception, retryableStatus(exception));
        }
    }

    /** 仅对限流和服务端故障重试，认证、请求和客户端错误直接失败。 */
    private boolean retryableStatus(Throwable exception) {
        if (!(exception instanceof RestClientResponseException responseException)) {
            return exception instanceof java.io.IOException;
        }
        int status = responseException.getStatusCode().value();
        return status == 429 || status >= 500;
    }
}
