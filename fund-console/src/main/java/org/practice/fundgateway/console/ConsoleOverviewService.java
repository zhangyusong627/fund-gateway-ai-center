package org.practice.fundgateway.console;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.practice.fundgateway.console.ConsoleModels.ConsoleOverview;
import org.practice.fundgateway.console.ConsoleModels.DependencyStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 汇总控制台首页需要的真实数据库计数和依赖探测结果。 */
@Service
public class ConsoleOverviewService {

    private final JdbcTemplate jdbcTemplate;
    private final ConsoleRagService ragService;
    private final GuardianConsoleService guardianService;
    private final String kafkaBootstrapServers;

    /** 注入数据库、知识库、智能守护及 Kafka 地址。 */
    public ConsoleOverviewService(JdbcTemplate jdbcTemplate, ConsoleRagService ragService,
                                  GuardianConsoleService guardianService,
                                  @Value("${KAFKA_BOOTSTRAP_SERVERS:localhost:19092}")
                                  String kafkaBootstrapServers) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragService = ragService;
        this.guardianService = guardianService;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    /** 读取当前真实运行数据，不返回任何预置演示数字。 */
    public ConsoleOverview load() {
        boolean postgresAvailable = postgresAvailable();
        int documentVersions = postgresAvailable ? count("select count(*) from knowledge.knowledge_documents") : 0;
        int indexedDocuments = postgresAvailable ? count("select count(*) from knowledge.knowledge_documents where status='INDEXED'") : 0;
        int indexedChunks = postgresAvailable ? count("select count(*) from knowledge.knowledge_chunks") : 0;
        int diagnosticTasks = postgresAvailable ? count("select count(*) from guardian.diagnosis_workflow_tasks where created_at >= current_date") : 0;
        int pendingApprovals = postgresAvailable ? count("select count(*) from guardian.diagnosis_workflow_tasks where status='PENDING_APPROVAL'") : 0;
        int modelCalls = postgresAvailable ? count("select count(*) from guardian.model_call_audits where called_at >= current_date") : 0;
        boolean embeddingAvailable = ragService.embeddingAvailable();
        boolean deepSeekAvailable = guardianService.deepSeekAvailable();
        return new ConsoleOverview(documentVersions, indexedDocuments, indexedChunks, diagnosticTasks,
                pendingApprovals, modelCalls,
                status(postgresAvailable, "PostgreSQL + pgvector"),
                status(redpandaAvailable(), "Redpanda"),
                status(embeddingAvailable, "BGE 本地模型"),
                status(deepSeekAvailable, "DeepSeek"));
    }

    /** 执行最小 SQL 确认 PostgreSQL 当前可访问。 */
    private boolean postgresAvailable() {
        try {
            return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("select 1", Integer.class));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 使用短连接探测 Redpanda Kafka 监听端口。 */
    private boolean redpandaAvailable() {
        String address = kafkaBootstrapServers.split(",")[0].trim();
        int separator = address.lastIndexOf(':');
        if (separator <= 0 || separator == address.length() - 1) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address.substring(0, separator),
                    Integer.parseInt(address.substring(separator + 1))), 500);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /** 执行返回单个整数的统计 SQL。 */
    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    /** 创建页面统一使用的依赖状态。 */
    private DependencyStatus status(boolean available, String label) {
        return new DependencyStatus(available, label);
    }
}
