package org.practice.fundgateway.guardian.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/** 读取内存中的合成资方契约，演示一个只读工具的注册和执行边界。 */
public class SyntheticContractQueryTool implements ToolCallback {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("querySyntheticContract")
            .description("查询合成资方接口的已确认契约")
            .inputSchema("{\"type\":\"object\",\"properties\":{\"provider\":{\"type\":\"string\"},\"interface\":{\"type\":\"string\"}},\"required\":[\"provider\",\"interface\"]}")
            .build();

    @Override
    public ToolDefinition getToolDefinition() {
        return DEFINITION;
    }

    /** 根据合成输入返回固定契约事实，不修改任何状态。 */
    @Override
    public String call(String toolInput) {
        SyntheticToolInput.require(toolInput, "只支持合成资方的授信申请查询");
        return "{\"provider\":\"synthetic-provider\",\"interface\":\"credit-apply\",\"qpsLimit\":20,\"timeoutMs\":3000}";
    }
}
