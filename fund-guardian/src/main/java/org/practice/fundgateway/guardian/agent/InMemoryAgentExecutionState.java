package org.practice.fundgateway.guardian.agent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 用于测试 Agent 中断后恢复游标的内存适配器。 */
public class InMemoryAgentExecutionState implements AgentExecutionStatePort {
    private final ConcurrentHashMap<String, AgentExecutionState> states = new ConcurrentHashMap<>();

    /** 按会话和任务幂等保存最新状态。 */
    @Override
    public void save(AgentExecutionState state) {
        states.put(key(state.conversationId(), state.diagnosticTaskId()), state);
    }

    /** 查询同一会话和诊断任务的执行状态。 */
    @Override
    public Optional<AgentExecutionState> find(String conversationId, UUID diagnosticTaskId) {
        return Optional.ofNullable(states.get(key(conversationId, diagnosticTaskId)));
    }

    /** 生成状态隔离键。 */
    private String key(String conversationId, UUID diagnosticTaskId) {
        return conversationId + "|" + diagnosticTaskId;
    }
}
