package org.practice.fundgateway.console;

import org.practice.fundgateway.guardian.memory.ConversationMemoryPort;
import org.practice.fundgateway.guardian.memory.InMemoryConversationMemory;
import org.practice.fundgateway.guardian.memory.JdbcConversationMemory;
import org.practice.fundgateway.guardian.agent.AgentExecutionStatePort;
import org.practice.fundgateway.guardian.agent.InMemoryAgentExecutionState;
import org.practice.fundgateway.guardian.agent.JdbcAgentExecutionState;
import org.practice.fundgateway.guardian.memory.IncidentMemoryPort;
import org.practice.fundgateway.guardian.memory.InMemoryIncidentMemory;
import org.practice.fundgateway.guardian.memory.JdbcIncidentMemory;
import org.practice.fundgateway.guardian.memory.ConfirmedIncidentMemoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** 装配控制台使用的会话记忆持久化适配器。 */
@Configuration
public class MemoryConsoleConfiguration {

    /** 创建审批后长期案例沉淀服务。 */
    @Bean
    public ConfirmedIncidentMemoryService confirmedIncidentMemoryService(IncidentMemoryPort incidentMemory) {
        return new ConfirmedIncidentMemoryService(incidentMemory);
    }

    /** 内存模式的长期案例仓储。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "memory")
    public IncidentMemoryPort inMemoryIncidentMemory() { return new InMemoryIncidentMemory(); }

    /** 正式模式的 PostgreSQL 长期案例仓储。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public IncidentMemoryPort jdbcIncidentMemory(JdbcTemplate jdbcTemplate) { return new JdbcIncidentMemory(jdbcTemplate); }

    /** 内存模式保存 Agent 执行游标。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "memory")
    public AgentExecutionStatePort inMemoryAgentExecutionState() {
        return new InMemoryAgentExecutionState();
    }

    /** 正式模式使用 PostgreSQL 保存 Agent 执行游标。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public AgentExecutionStatePort jdbcAgentExecutionState(JdbcTemplate jdbcTemplate) {
        return new JdbcAgentExecutionState(jdbcTemplate);
    }

    /** 内存模式用于无数据库本地演示和测试。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "memory")
    public ConversationMemoryPort inMemoryConversationMemory() {
        return new InMemoryConversationMemory();
    }

    /** 正式模式使用 PostgreSQL 保存会话消息和摘要。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public ConversationMemoryPort jdbcConversationMemory(JdbcTemplate jdbcTemplate) {
        return new JdbcConversationMemory(jdbcTemplate);
    }
}
