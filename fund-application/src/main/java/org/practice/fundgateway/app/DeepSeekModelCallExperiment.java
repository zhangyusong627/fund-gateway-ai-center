package org.practice.fundgateway.app;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** D1 同步模型调用实验，不包含工具、持久化和自动重试。 */
@Component
@Profile("d1")
public class DeepSeekModelCallExperiment implements CommandLineRunner {
    @Override
    public void run(String... args) {
        try {
            executeModelCallExperiment();
        } catch (Exception failure) {
            // 服务端异常信息可能包含请求或响应内容，因此只抛出异常类型。
            throw new IllegalStateException("D1 failed: " + failure.getClass().getSimpleName());
        }
    }

    /** 执行一次脱敏的 DeepSeek 同步调用并保存实验凭证。 */
    private void executeModelCallExperiment() throws IOException {
        String key = requireEnvironmentVariable("DEEPSEEK_API_KEY");
        String prompt = requireEnvironmentVariable("D1_PROMPT");
        Path evidence = Path.of("docs/learning/D1-call-" + System.currentTimeMillis());
        Files.createDirectory(evidence);
        var mapper = JsonMapper.builder().build();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        var client = RestClient.builder().requestFactory(factory).requestInterceptor((request, body, execution) -> {
            ObjectNode safeRequest = (ObjectNode) mapper.readTree(body);
            safeRequest.withArray("messages").forEach(message -> ((ObjectNode) message).put("content", "[REDACTED]"));
            saveEvidence(evidence.resolve("request.http"), request.getMethod() + " " + request.getURI()
                    + "\nContent-Type: " + request.getHeaders().getContentType()
                    + "\nAuthorization: [REDACTED]\n\n" + mapper.writeValueAsString(safeRequest));
            try (ClientHttpResponse response = execution.execute(request, body)) {
                byte[] raw = response.getBody().readAllBytes();
                String text = new String(raw, StandardCharsets.UTF_8);
                // 只有响应没有回显密钥和提示词时，才保存原始响应字节。
                boolean redacted = text.contains(key) || text.contains(prompt)
                        || text.contains(mapper.writeValueAsString(prompt).substring(1, mapper.writeValueAsString(prompt).length() - 1));
                if (redacted) {
                    ObjectNode safeResponse = (ObjectNode) mapper.readTree(raw);
                    safeResponse.remove("choices");
                    safeResponse.remove("error");
                    saveEvidence(evidence.resolve("response.redacted.json"), safeResponse.toString()
                            .replace(key, "[REDACTED]").replace(prompt, "[REDACTED]"));
                } else {
                    Files.write(evidence.resolve("response.json"), raw, StandardOpenOption.CREATE_NEW);
                }
                saveEvidence(evidence.resolve("response.http"), "HTTP " + response.getStatusCode().value()
                        + "\nContent-Type: " + response.getHeaders().getContentType()
                        + "\nBody: " + (redacted ? "response.redacted.json (echo removed)" : "response.json (original bytes)") + "\n");
                return new BufferedResponse(response.getStatusCode(), response.getStatusText(), response.getHeaders(), raw);
            }
        });
        var api = DeepSeekApi.builder().baseUrl("https://api.deepseek.com").apiKey(key)
                .restClientBuilder(client).responseErrorHandler(new ResponseErrorHandler() {
                    public boolean hasError(ClientHttpResponse response) throws IOException {
                        return response.getStatusCode().isError();
                    }
                    public void handleError(java.net.URI url, org.springframework.http.HttpMethod method,
                                            ClientHttpResponse response) throws IOException {
                        throw new IOException("Provider HTTP " + response.getStatusCode().value());
                    }
                }).build();
        var request = ChatCompletionRequest.builder().model("deepseek-v4-flash")
                .messages(List.of(new ChatCompletionMessage(prompt, ChatCompletionMessage.Role.USER)))
                .stream(false).maxTokens(128).thinking(ChatCompletionRequest.Thinking.DISABLED).build();
        long started = System.nanoTime();
        var response = api.chatCompletionEntity(request);
        if (response.getBody() == null || response.getBody().usage() == null) {
            throw new IOException("Missing response usage");
        }
        saveEvidence(evidence.resolve("result.txt"), "HTTP=" + response.getStatusCode().value()
                + "\nelapsed_ms=" + (System.nanoTime() - started) / 1_000_000
                + "\nusage=" + mapper.writeValueAsString(response.getBody().usage()) + "\n");
        System.out.println("D1 call completed; evidence=" + evidence);
    }

    /** 读取必需的环境变量，不在异常信息中暴露变量值。 */
    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }

    /** 将实验凭证写入指定文件。 */
    private static void saveEvidence(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private record BufferedResponse(HttpStatusCode status, String text, HttpHeaders headers, byte[] body)
            implements ClientHttpResponse {
        public HttpStatusCode getStatusCode() { return status; }
        public String getStatusText() { return text; }
        public HttpHeaders getHeaders() { return headers; }
        public InputStream getBody() { return new ByteArrayInputStream(body); }
        public void close() { }
    }
}
