package org.practice.fundgateway.guardian.memory;

import java.util.StringJoiner;

/** 将摘要、最近消息和工具轨迹明确拼接为本次模型可见的上下文。 */
public class AgentContextPromptBuilder {

    /** 构建当前用户问题前的历史上下文，避免把原始数据库记录全部发送给模型。 */
    public String build(AgentContext context, String currentPrompt) {
        if (context == null || currentPrompt == null || currentPrompt.isBlank()) {
            throw new IllegalArgumentException("Agent 上下文和当前问题不能为空");
        }
        StringBuilder result = new StringBuilder();
        if (context.summary() != null) {
            result.append("历史会话摘要：\n").append(context.summary().content()).append("\n\n");
        }
        if (!context.recentMessages().isEmpty()) {
            result.append("最近会话消息：\n");
            StringJoiner messages = new StringJoiner("\n");
            context.recentMessages().forEach(message -> messages.add("[" + message.role() + "] " + message.content()));
            result.append(messages).append("\n\n");
        }
        if (!context.toolTrace().isEmpty()) {
            result.append("本轮工具轨迹：\n").append(String.join("\n", context.toolTrace())).append("\n\n");
        }
        return result + "当前问题：\n" + currentPrompt;
    }
}
