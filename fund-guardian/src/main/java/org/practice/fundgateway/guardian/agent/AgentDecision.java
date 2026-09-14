package org.practice.fundgateway.guardian.agent;

/** 表示模型对下一步 Agent 行动的结构化选择。 */
public record AgentDecision(Kind kind, String toolName, String toolInput, String finalContent) {

    /** 校验不同决策类型所需字段。 */
    public AgentDecision {
        if (kind == null) throw new IllegalArgumentException("Agent 决策类型不能为空");
        if (kind == Kind.TOOL_CALL && (toolName == null || toolName.isBlank()
                || toolInput == null || toolInput.isBlank())) {
            throw new IllegalArgumentException("工具决策缺少工具名称或参数");
        }
        if (kind == Kind.FINAL && (finalContent == null || finalContent.isBlank())) {
            throw new IllegalArgumentException("最终决策缺少结果");
        }
    }

    /** Agent 每轮只能请求工具或结束。 */
    public enum Kind { TOOL_CALL, FINAL }

    /** 创建工具调用决策。 */
    public static AgentDecision tool(String name, String input) {
        return new AgentDecision(Kind.TOOL_CALL, name, input, null);
    }

    /** 创建最终回答决策。 */
    public static AgentDecision finalAnswer(String content) {
        return new AgentDecision(Kind.FINAL, null, null, content);
    }
}
