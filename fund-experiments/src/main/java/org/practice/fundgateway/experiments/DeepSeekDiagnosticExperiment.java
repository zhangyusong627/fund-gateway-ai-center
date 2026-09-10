package org.practice.fundgateway.experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.practice.fundgateway.guardian.tool.SyntheticDiagnosticToolRegistry;
import org.practice.fundgateway.guardian.diagnosis.*;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.deepseek.api.DeepSeekApi.FunctionTool;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** M1 真实诊断实验：模型读取证据，Java 保证工具白名单和只读边界。 */
@Component
// 只有显式启用 m1-real profile 时才执行真实模型调用，默认启动不会产生外部请求。
@Profile("m1-real")
public class DeepSeekDiagnosticExperiment implements CommandLineRunner {

    /** 固定本次实验使用的模型标识，避免运行时静默切换模型。 */
    private static final String MODEL = "deepseek-v4-flash";

    /** 负责保存请求响应、解析工具 JSON，以及序列化 Java 规则基线。 */
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 集中维护允许模型调用的三个只读工具，并按工具名执行白名单查找。 */
    private final SyntheticDiagnosticToolRegistry registry = new SyntheticDiagnosticToolRegistry();

    /** Spring Boot 完成启动后自动执行一次 M1 真实诊断实验。 */
    @Override
    public void run(String... args) {
        try {
            executeOnce();
        } catch (Exception e) {
            // 对外只暴露异常类型，不把密钥、请求正文等上下文拼入启动异常。
            throw new IllegalStateException("M1 真实诊断失败: " + e.getClass().getSimpleName());
        }
    }

    /** 执行模型选工具、Java 执行工具、结果回灌的两轮流程。 */
    private void executeOnce() throws IOException {
        // 密钥只从当前进程环境变量读取，不进入代码、配置和证据文件。
        String key = requireEnv("DEEPSEEK_API_KEY");

        // 每次实验创建独立时间戳目录，防止新证据覆盖历史调用证据。
        Path evidence = Path.of("..", "docs", "learning", "M1-real-call-" + System.currentTimeMillis());
        Files.createDirectories(evidence);

        // 这里直接使用 DeepSeek 底层 API，以便完整观察两轮请求和工具协议报文。
        DeepSeekApi api = DeepSeekApi.builder().baseUrl("https://api.deepseek.com").apiKey(key).build();

        // 只把注册表中的白名单工具转换成 DeepSeek 能识别的 function tools。
        List<FunctionTool> definitions = registry.tools().stream().map(t -> new FunctionTool(
                new FunctionTool.Function(t.getToolDefinition().description(), t.getToolDefinition().name(),
                        t.getToolDefinition().inputSchema()))).toList();

        // 提示模型必须先取齐三类证据，并明确限制最终结果只能是只读建议。
        String prompt = "诊断 synthetic-provider 的 credit-apply 接口。必须先读取契约、运行指标和历史故障，只给出只读治理建议，不执行配置修改。最终只能返回 JSON，字段必须为 summary、riskLevel、findings、requiresHumanReview；riskLevel 只能是 LOW、MEDIUM、HIGH；findings 每项必须包含 ruleId、matched、evidence、recommendation、requiresHumanReview；ruleId 只能使用 R001、R002、R003、R004、R005，不得自定义规则名；summary、evidence、recommendation 必须使用简体中文，技术标识可保留英文；不要输出 Markdown、解释文字或代码围栏。";

        // 首轮请求携带工具定义，让模型只负责判断需要调用哪些工具及生成调用参数。
        ChatCompletionRequest request = ChatCompletionRequest.builder().model(MODEL)
                .messages(List.of(new ChatCompletionMessage(prompt, Role.USER))).tools(definitions).stream(false).build();

        // 在调用模型前保存首轮完整请求，用于核对实际发送的消息和工具定义。
        String requestOne = mapper.writeValueAsString(request);
        saveAndPrint(evidence.resolve("request-1.json"), requestOne, "首轮请求");

        // 发起首轮模型调用；预期响应不是最终诊断，而是一组工具调用请求。
        var first = api.chatCompletionEntity(request).getBody();

        // 保存模型原始首轮响应，保留工具调用名称、参数和 call id。
        String responseOne = mapper.writeValueAsString(first);
        saveAndPrint(evidence.resolve("response-1.json"), responseOne, "首轮响应");

        // 取出模型消息，其中包含后续需要逐个执行的 toolCalls。
        ChatCompletionMessage assistant = first.choices().getFirst().message();

        // 二轮上下文必须同时保留原始用户消息和模型的工具调用消息。
        List<ChatCompletionMessage> messages = new ArrayList<>(List.of(new ChatCompletionMessage(prompt, Role.USER), assistant));

        // 分别暂存三类工具结果，后续据此构造确定性规则服务的唯一输入。
        Map<String, Object> contractJson = null;
        Map<String, Object> metricsJson = null;
        Map<String, Object> incidentJson = null;

        // index 只用于生成顺序稳定、互不覆盖的工具结果证据文件名。
        int index = 0;

        // 逐个处理模型请求的工具调用，不允许模型直接执行任意 Java 方法。
        for (ToolCall call : assistant.toolCalls()) {
            // 按模型返回的工具名查询白名单；未注册名称会立即失败。
            var tool = registry.require(call.function().name());

            // Java 侧执行只读工具，模型提供的 arguments 只是输入，不能绕过工具实现边界。
            String result = tool.call(call.function().arguments());

            // 每个工具原始结果单独落盘，便于追踪最终结论来自哪份证据。
            saveAndPrint(evidence.resolve("tool-result-" + (++index) + ".json"), result, "工具结果 " + index);

            // 工具统一返回 JSON 字符串，此处先解析为 Map，再按工具类型归入对应证据槽位。
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(result, Map.class);
            if (call.function().name().equals("querySyntheticContract")) {
                contractJson = parsed;
            }
            if (call.function().name().equals("querySyntheticMetrics")) {
                metricsJson = parsed;
            }
            if (call.function().name().equals("querySyntheticIncidentHistory")) {
                incidentJson = parsed;
            }

            // 通过 call id 将工具结果与首轮的具体工具调用关联起来，再交给第二轮模型读取。
            messages.add(new ChatCompletionMessage(result, Role.TOOL, null, call.id(), null));
        }

        // 只使用三个工具的真实返回值构造规则证据，禁止另外写死一套“正确答案”。
        DiagnosisEvidence evidenceModel = new DiagnosisEvidence(
                // 将契约工具结果转换为资方、接口、QPS 上限和超时上限。
                new ContractEvidence(text(contractJson, "provider"), text(contractJson, "interface"),
                        integer(contractJson, "qpsLimit"), integer(contractJson, "timeoutMs")),
                // 将指标工具结果转换为当前流量、延迟、超时率和线程池状态。
                new MetricsEvidence(text(metricsJson, "provider"), text(metricsJson, "interface"),
                        integer(metricsJson, "qps"), integer(metricsJson, "avgLatencyMs"),
                        decimal(metricsJson, "timeoutRate"), integer(metricsJson, "activeThreads"),
                        integer(metricsJson, "maxThreads")),
                // 将历史故障工具结果转换为可供诊断引用的已确认事件证据。
                new IncidentEvidence(text(incidentJson, "provider"), text(incidentJson, "interface"),
                        text(incidentJson, "incidentId"), text(incidentJson, "status"),
                        text(incidentJson, "cause"), text(incidentJson, "occurredAt")));

        // Java 固定规则独立计算基线，模型只能提供建议，不能替代确定性阈值判断。
        var baseline = new DeterministicDiagnosisService().diagnose(evidenceModel);
        saveAndPrint(evidence.resolve("deterministic-baseline.json"), mapper.writeValueAsString(baseline), "Java 规则基线");

        // 二轮请求带回完整对话和工具结果，不再提供工具定义，要求模型基于现有证据生成最终诊断。
        ChatCompletionRequest second = ChatCompletionRequest.builder().model(MODEL).messages(messages).stream(false).build();

        // 在调用前保存二轮请求，以验证 assistant toolCalls 与 tool responses 的关联是否完整。
        String requestTwo = mapper.writeValueAsString(second);
        saveAndPrint(evidence.resolve("request-2.json"), requestTwo, "二轮请求");

        // 发起第二轮调用并保存模型最终诊断响应。
        var finalResponse = api.chatCompletionEntity(second).getBody();
        String finalRaw = mapper.writeValueAsString(finalResponse);
        saveAndPrint(evidence.resolve("response-2.json"), finalRaw, "最终响应");
        String finalContent = finalResponse.choices().getFirst().message().content();
        ModelDiagnosisReport report = mapper.readValue(finalContent, ModelDiagnosisReport.class);
        ModelDiagnosisReport accepted = new ModelDiagnosisGate().validate(report);
        saveAndPrint(evidence.resolve("validated-model-report.json"), mapper.writeValueAsString(accepted), "门禁通过的模型报告");

        // 控制台只输出证据目录，不输出密钥。
        System.out.println("M1 真实诊断完成，证据目录=" + evidence);
    }

    /** 读取环境变量密钥，不在日志中输出。 */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            // 缺少必需变量时快速失败，避免携带空密钥发出无效外部请求。
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    /** 以 UTF-8 保存原始报文。 */
    private static void save(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    /** 从解析后的工具结果读取文本字段。 */
    private static String text(Map<String, Object> values, String key) {
        if (values == null || values.get(key) == null) {
            // 工具未调用或返回缺字段时停止诊断，不允许用默认值伪造完整证据。
            throw new IllegalStateException("工具结果缺少字段: " + key);
        }
        return String.valueOf(values.get(key));
    }

    /** 从解析后的工具结果读取整数。 */
    private static int integer(Map<String, Object> values, String key) {
        // 先复用非空检查，再让非法数字通过 NumberFormatException 明确失败。
        return Integer.parseInt(text(values, key));
    }

    /** 从解析后的工具结果读取小数。 */
    private static double decimal(Map<String, Object> values, String key) {
        // 超时率等比例值保留小数，非法格式不做静默兜底。
        return Double.parseDouble(text(values, key));
    }

    /** 保存并打印完整原始报文，便于学习者核对每一跳的协议内容。 */
    private static void saveAndPrint(Path path, String content, String label) throws IOException {
        // 先落盘再打印，确保控制台输出与证据文件使用同一份内容。
        save(path, content);
        System.out.println("===== " + label + " =====");
        System.out.println(content);
        System.out.println("===== " + label + " 结束 =====");
    }
}
