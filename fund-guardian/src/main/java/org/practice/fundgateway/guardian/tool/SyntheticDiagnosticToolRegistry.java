package org.practice.fundgateway.guardian.tool;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;

/**
 * 维护 M1 只读诊断工具白名单，避免模型调用未声明的工具。
 */
public class SyntheticDiagnosticToolRegistry {

    private final List<ToolCallback> tools = List.of(new SyntheticContractQueryTool(),
            new SyntheticMetricsQueryTool(), new SyntheticIncidentHistoryTool());

    /**
     * 返回不可变的只读工具集合。
     */
    public List<ToolCallback> tools() {
        return tools;
    }

    /**
     * 按模型返回的工具名查找工具，未知名称直接拒绝。
     */
    public ToolCallback require(String name) {
        return tools.stream().filter(tool -> tool.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("未注册的诊断工具: " + name));
    }
}
