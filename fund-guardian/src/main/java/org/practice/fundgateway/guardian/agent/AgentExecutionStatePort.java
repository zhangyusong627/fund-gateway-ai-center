package org.practice.fundgateway.guardian.agent;

import java.util.Optional;
import java.util.UUID;

/** 定义 Agent 执行游标的保存和恢复端口。 */
public interface AgentExecutionStatePort {
    /** 保存最新执行游标。 */
    void save(AgentExecutionState state);

    /** 按会话和任务读取游标。 */
    Optional<AgentExecutionState> find(String conversationId, UUID diagnosticTaskId);
}
