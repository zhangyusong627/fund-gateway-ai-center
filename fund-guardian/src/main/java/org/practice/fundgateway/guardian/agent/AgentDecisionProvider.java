package org.practice.fundgateway.guardian.agent;

import org.practice.fundgateway.guardian.memory.AgentContext;

/** 定义模型决策适配器，Agent 编排不依赖具体 AI SDK。 */
@FunctionalInterface
public interface AgentDecisionProvider {

    /** 根据当前上下文决定调用工具或结束。 */
    AgentDecision decide(AgentContext context);
}
