package org.practice.fundgateway.guardian.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/** 查询合成资方接口的只读历史故障记录。 */
public class SyntheticIncidentHistoryTool implements ToolCallback {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("querySyntheticIncidentHistory")
            .description("查询合成资方接口近期已确认的历史故障")
            .inputSchema("{\"type\":\"object\",\"properties\":{\"provider\":{\"type\":\"string\"},\"interface\":{\"type\":\"string\"}},\"required\":[\"provider\",\"interface\"]}")
            .build();

    /** 返回工具定义。 */
    @Override
    public ToolDefinition getToolDefinition() {
        return DEFINITION;
    }

    /** 根据合成资方和接口返回固定历史故障。 */
    @Override
    public String call(String toolInput) {
        if (toolInput == null || !toolInput.contains("synthetic-provider")
                || !toolInput.contains("credit-apply")) {
            throw new IllegalArgumentException("只支持合成资方的授信申请历史查询");
        }
        return "{\"provider\":\"synthetic-provider\",\"interface\":\"credit-apply\","
                + "\"incidentId\":\"incident-001\",\"status\":\"confirmed\","
                + "\"cause\":\"upstream-timeout\",\"occurredAt\":\"2026-09-08T10:00:00+08:00\"}";
    }
}
