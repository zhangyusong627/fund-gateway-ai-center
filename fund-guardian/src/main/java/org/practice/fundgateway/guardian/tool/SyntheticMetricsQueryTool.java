package org.practice.fundgateway.guardian.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/** 查询合成资方接口的只读运行指标。 */
public class SyntheticMetricsQueryTool implements ToolCallback {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("querySyntheticMetrics")
            .description("查询合成资方接口的 QPS、响应时间和超时率")
            .inputSchema("{\"type\":\"object\",\"properties\":{\"provider\":{\"type\":\"string\"},\"interface\":{\"type\":\"string\"}},\"required\":[\"provider\",\"interface\"]}")
            .build();

    /** 返回工具定义。 */
    @Override
    public ToolDefinition getToolDefinition() {
        return DEFINITION;
    }

    /** 根据合成资方和接口返回固定运行指标。 */
    @Override
    public String call(String toolInput) {
        SyntheticToolInput.require(toolInput, "只支持合成资方的授信申请指标查询");
        return "{\"provider\":\"NYXJ\",\"interface\":\"credit-apply\","
                + "\"qps\":18,\"avgLatencyMs\":420,\"timeoutRate\":0.02,"
                + "\"activeThreads\":6,\"maxThreads\":10}";
    }
}
