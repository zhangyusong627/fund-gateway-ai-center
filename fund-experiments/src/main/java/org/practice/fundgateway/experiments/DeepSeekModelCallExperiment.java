package org.practice.fundgateway.experiments;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * D1 同步模型调用实验，不包含工具、持久化和自动重试。
 *
 * <p>目标：用真实密钥向 DeepSeek 发一次同步 chat 请求，并把这次调用的证据
 * （脱敏请求、脱敏/原始响应、HTTP 状态、耗时、token 用量）落盘到
 * {@code docs/learning/D1-call-<时间戳>/} 目录。
 *
 * <p>安全设计贯穿全类：
 * <ol>
 *   <li>密钥与提示词只从环境变量读入，代码与证据中不出现字面量；</li>
 *   <li>落盘的请求副本把消息正文打成 {@code [REDACTED]}、鉴权头直接打码；</li>
 *   <li>落盘响应前先检查是否回显了密钥/提示词，回显则只留元数据、删掉正文。</li>
 * </ol>
 *
 * <p>仅当激活 {@code d1} profile 时才创建本组件；由 Spring Boot 在启动阶段
 * 自动调用 {@link #run}（实现 {@link CommandLineRunner} 的组件都会被执行）。
 */
@Component
@Profile("d1")
public class DeepSeekModelCallExperiment implements CommandLineRunner {

    /** 连接超时：TCP 建连失败（断网/DNS/防火墙）时快速报错，不长时间空等。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 读超时：建连后等待回包的时限。大模型生成慢，故放宽到 60 秒。 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    /** 输出 token 上限：只约束模型回答的长度，管不了发出去的 prompt。 */
    private static final int MAX_OUTPUT_TOKENS = 128;
    /** DeepSeek 服务地址（兼容 OpenAI 协议）。 */
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    /** 本次实验使用的模型名（技术基线已冻结，勿改为 deepseek-chat）。 */
    private static final String DEEPSEEK_MODEL = "deepseek-v4-flash";
    /** 证据落盘的根目录。 */
    private static final String EVIDENCE_ROOT = "docs/learning";

    /**
     * 本地学习用调试开关（默认关）。开启后把「完整请求体/响应体」打到控制台，方便观察
     * 实际发出和收到的参数；证据文件仍走脱敏逻辑，不因开启而写入密钥或提示词。
     * 启动示例：java -Dd1.debug=true -jar ...
     */
    private static final boolean DEBUG_PRINT =
            Boolean.parseBoolean(System.getProperty("d1.debug", "false"));

    /** JSON 工具：解析请求/响应做脱敏、把 usage 计量序列化成字符串。 */
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /** 运行者通过环境变量注入的两项输入。 */
    private record Credentials(String apiKey, String prompt) {
    }

    @Override
    public void run(String... args) {
        try {
            executeModelCallExperiment();
        } catch (Exception failure) {
            // 只抛异常类型：服务端异常信息可能夹带请求/响应原文，不能带出到日志。
            throw new IllegalStateException("D1 failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 主流程，从上到下就是这次实验的全部，拆成五个小步骤：
     * <ol>
     *   <li>从环境变量读取输入（密钥、提示词）；</li>
     *   <li>建本次实验的证据目录（时间戳命名，互不覆盖）；</li>
     *   <li>组装传输层：超时配置 + 脱敏拦截器 + DeepSeek 客户端；</li>
     *   <li>组装请求体（模型、消息、输出上限等显式关心的字段）；</li>
     *   <li>真正发出请求，记录 HTTP 状态、耗时与 token 用量。</li>
     * </ol>
     */
    private void executeModelCallExperiment() throws IOException {
        Credentials credentials = loadCredentials();                        // ① 原材料：key + prompt
        Path evidenceDir = createEvidenceDirectory();                       // ② 本次证据目录
        DeepSeekApi api = buildDeepSeekApi(credentials, evidenceDir);       // ③ 传输层（地址/超时/脱敏）
        ChatCompletionRequest request = buildChatRequest(credentials.prompt()); // ④ 请求体内容
        callAndRecordUsage(api, request, evidenceDir);                      // ⑤ 发请求并留存用量证据
        System.out.println("D1 call completed; evidence=" + evidenceDir);
    }

    /** ① 只从环境变量读，保证代码里没有任何密钥/提示词字面量。 */
    private Credentials loadCredentials() {
        return new Credentials(requireEnv("DEEPSEEK_API_KEY"), requireEnv("D1_PROMPT"));
    }

    /** 读取必需的环境变量；变量名进异常信息，变量值永不进入异常信息。 */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        return value;
    }

    /** ② 每次运行建一个带时间戳的目录，证据互不覆盖，也便于和调用时刻对应。 */
    private Path createEvidenceDirectory() throws IOException {
        Path dir = Path.of(EVIDENCE_ROOT, "D1-call-" + System.currentTimeMillis());
        Files.createDirectory(dir);
        return dir;
    }

    /**
     * ③ 组装传输层。分三层理解：
     * <ul>
     *   <li>最底层 {@link SimpleClientHttpRequestFactory}：只负责 TCP 连接与读写超时；</li>
     *   <li>中间层 {@link RestClient.Builder}：通过 requestInterceptor 挂上脱敏拦截器，
     *       让每个请求在发出前后都被"先脱敏存档、再放行"；</li>
     *   <li>最上层 {@link DeepSeekApi}：Spring AI 提供的 DeepSeek 客户端，绑定上面的
     *       RestClient 与自定义错误处理器。</li>
     * </ul>
     */
    private DeepSeekApi buildDeepSeekApi(Credentials credentials, Path evidenceDir) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new RedactingInterceptor(jsonMapper, credentials, evidenceDir));

        return DeepSeekApi.builder()
                .baseUrl(DEEPSEEK_BASE_URL)
                .apiKey(credentials.apiKey())
                .restClientBuilder(restClientBuilder)
                .responseErrorHandler(new ProviderStatusErrorHandler())
                .build();
    }

    /**
     * ④ 组装请求体。只显式声明关心的字段（模型/消息/流式/输出上限/思考开关），
     * 其余采样参数（temperature、top_p 等）没写，交给 SDK 默认值。
     */
    private ChatCompletionRequest buildChatRequest(String prompt) {
        return ChatCompletionRequest.builder()
                .model(DEEPSEEK_MODEL)
                .messages(List.of(new ChatCompletionMessage(prompt, ChatCompletionMessage.Role.USER)))
                .stream(false)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .thinking(ChatCompletionRequest.Thinking.DISABLED)
                .build();
    }

    /**
     * ⑤ 发出同步请求并留存用量证据。
     * <ul>
     *   <li>{@code chatCompletionEntity} 是阻塞调用，耗时主要取决于模型生成速度；</li>
     *   <li>usage 为空说明没拿到 token 计量，视为实验失败；</li>
     *   <li>结果写入 {@code result.txt}：HTTP 状态、端到端耗时、token 用量。</li>
     * </ul>
     */
    private void callAndRecordUsage(DeepSeekApi api, ChatCompletionRequest request, Path evidenceDir)
            throws IOException {
        long started = System.nanoTime();
        var response = api.chatCompletionEntity(request);
        if (response.getBody() == null || response.getBody().usage() == null) {
            throw new IOException("Missing response usage");
        }
        saveEvidence(evidenceDir.resolve("result.txt"),
                "HTTP=" + response.getStatusCode().value()
                        + "\nelapsed_ms=" + (System.nanoTime() - started) / 1_000_000
                        + "\nusage=" + jsonMapper.writeValueAsString(response.getBody().usage()) + "\n");
    }

    /** 把字符串写入证据文件；CREATE_NEW 防止同名覆盖，写重说明设计有误。 */
    private static void saveEvidence(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    /**
     * 脱敏拦截器——本类安全性的核心。
     *
     * <p>每个请求都会穿过这里，它做三件事：
     * <ol>
     *   <li>发出前：把请求体里所有消息的 content 换成 {@code [REDACTED]}，连同
     *       方法/URL/头存成 {@code request.http}，Authorization 直接打码；</li>
     *   <li>真实请求照发，并把响应体完整读进内存（读完即关闭底层流）；</li>
     *   <li>落盘响应前先做"回显检查"：回包里出现密钥/提示词就只存删掉正文的脱敏版，
     *       否则存原始字节。</li>
     * </ol>
     *
     * <p>因为拦截器先消费过响应体，最后用缓存字节包一个 {@link BufferedResponse} 交还
     * 上层，保证 DeepSeekApi 反序列化时还能再读到完整内容。
     */
    private static class RedactingInterceptor implements ClientHttpRequestInterceptor {

        private final JsonMapper jsonMapper;
        private final String apiKey;
        private final String prompt;
        private final Path evidenceDir;

        RedactingInterceptor(JsonMapper jsonMapper, Credentials credentials, Path evidenceDir) {
            this.jsonMapper = jsonMapper;
            this.apiKey = credentials.apiKey();
            this.prompt = credentials.prompt();
            this.evidenceDir = evidenceDir;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            printRequestIfDebug(request, body);                             // 学习用：完整请求打到控制台
            saveRedactedRequestEvidence(request, body);                     // ① 出口：请求先脱敏存档
            try (ClientHttpResponse response = execution.execute(request, body)) { // ② 真实请求照发
                byte[] rawBody = readRawResponseBody(response);             // ③ 完整读回包
                saveResponseEvidence(response, rawBody);                    // ④ 入口：查回显后落盘
                return new BufferedResponse(response.getStatusCode(), response.getStatusText(),
                        response.getHeaders(), rawBody);                    // ⑤ 用缓存字节交还上层
            }
        }

        /** ① 请求存档副本：正文消息内容与 Authorization 头都换成 [REDACTED]。 */
        private void saveRedactedRequestEvidence(HttpRequest request, byte[] body) throws IOException {
            ObjectNode safeRequest = (ObjectNode) jsonMapper.readTree(body);
            safeRequest.withArray("messages")
                    .forEach(message -> ((ObjectNode) message).put("content", "[REDACTED]"));
            saveEvidence(evidenceDir.resolve("request.http"),
                    request.getMethod() + " " + request.getURI()
                            + "\nContent-Type: " + request.getHeaders().getContentType()
                            + "\nAuthorization: [REDACTED]\n\n" + jsonMapper.writeValueAsString(safeRequest));
        }

        /** 学习用：把即将发出的完整请求体打到控制台。请求体 JSON 里不含 API 密钥（密钥只在头里），故可打印。 */
        private void printRequestIfDebug(HttpRequest request, byte[] body) {
            if (!DeepSeekModelCallExperiment.DEBUG_PRINT) {
                return;
            }
            System.out.println("[debug] >>> 完整请求 (不含 Authorization 头)");
            System.out.println(request.getMethod() + " " + request.getURI());
            System.out.println(new String(body, StandardCharsets.UTF_8));
            System.out.println();
        }

        /** ③ 把响应体整体读入内存并关闭输入流；此后都用这份字节缓存操作。 */
        private static byte[] readRawResponseBody(ClientHttpResponse response) throws IOException {
            try (InputStream in = response.getBody()) {
                return in.readAllBytes();
            }
        }

        /**
         * ④ 响应落盘前的分流：无回显 → 存原始字节 {@code response.json}；
         * 有回显 → 只存删掉正文的脱敏版 {@code response.redacted.json}。
         * 另写 {@code response.http} 说明本次存了哪个文件。
         */
        private void saveResponseEvidence(ClientHttpResponse response, byte[] rawBody) throws IOException {
            String responseText = new String(rawBody, StandardCharsets.UTF_8);
            boolean echoed = responseEchoesSensitiveContent(responseText);
            if (echoed) {
                saveEchoRedactedEvidence(rawBody);
            } else {
                Files.write(evidenceDir.resolve("response.json"), rawBody, StandardOpenOption.CREATE_NEW);
            }
            saveEvidence(evidenceDir.resolve("response.http"),
                    "HTTP " + response.getStatusCode().value()
                            + "\nContent-Type: " + response.getHeaders().getContentType()
                            + "\nBody: " + (echoed
                                    ? "response.redacted.json (echo removed)"
                                    : "response.json (original bytes)") + "\n");
            printResponseIfDebug(response, rawBody, echoed);                // 学习用：完整响应打到控制台
        }

        /** 学习用：打印完整响应。若检测到回显密钥/提示词则不打印正文，防止泄露。 */
        private void printResponseIfDebug(ClientHttpResponse response, byte[] rawBody, boolean echoed)
                throws IOException {
            if (!DeepSeekModelCallExperiment.DEBUG_PRINT) {
                return;
            }
            System.out.println("[debug] <<< 完整响应 (HTTP " + response.getStatusCode().value() + ")");
            if (echoed) {
                System.out.println("[debug] 响应回显了密钥/提示词，为安全不打印正文；脱敏版见 response.redacted.json");
            } else {
                System.out.println(new String(rawBody, StandardCharsets.UTF_8));
            }
            System.out.println();
        }

        /**
         * 回显检查：响应文本中出现下面任一种都视为"把敏感内容回显出来了"——
         * <ol>
         *   <li>API 密钥原文；</li>
         *   <li>提示词原文；</li>
         *   <li>提示词被 JSON 转义后的写法（服务商常按 JSON 字符串原样回显）。</li>
         * </ol>
         */
        private boolean responseEchoesSensitiveContent(String responseText) throws IOException {
            return responseText.contains(apiKey)
                    || responseText.contains(prompt)
                    || responseText.contains(jsonEscapedPrompt());
        }

        /**
         * 提示词在 JSON 字符串里的样子：{@code writeValueAsString} 会加首尾双引号并做转义，
         * 截掉首尾引号后正好匹配回包 JSON 中 content 值内部的写法。
         */
        private String jsonEscapedPrompt() throws IOException {
            String quoted = jsonMapper.writeValueAsString(prompt);
            return quoted.substring(1, quoted.length() - 1);
        }

        /** 回显分支：宁可丢答案也不泄密——删掉正文所在字段，再替换残留的密钥/提示词。 */
        private void saveEchoRedactedEvidence(byte[] rawBody) throws IOException {
            ObjectNode safeResponse = (ObjectNode) jsonMapper.readTree(rawBody);
            safeResponse.remove("choices"); // 模型回答在 choices 里
            safeResponse.remove("error");   // 错误体同样可能回显请求内容
            saveEvidence(evidenceDir.resolve("response.redacted.json"),
                    safeResponse.toString().replace(apiKey, "[REDACTED]").replace(prompt, "[REDACTED]"));
        }
    }

    /** 服务商返回 4xx/5xx 时只抛状态码：响应体可能含请求/响应内容，不放进异常传播。 */
    private static class ProviderStatusErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
            throw new IOException("Provider HTTP " + response.getStatusCode().value());
        }
    }

    /**
     * 把真实响应整段缓存后再交还上层：拦截器已经消费过底层 body 流，
     * 必须用缓存字节构造一个可重复读取的响应，DeepSeekApi 反序列化时才能再次读到内容。
     */
    private record BufferedResponse(HttpStatusCode status, String text, HttpHeaders headers, byte[] body)
            implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            return text;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
        }
    }
}
