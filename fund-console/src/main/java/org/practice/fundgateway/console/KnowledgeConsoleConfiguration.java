package org.practice.fundgateway.console;

import org.practice.fundgateway.knowledge.document.DocumentVersionRepository;
import org.practice.fundgateway.knowledge.document.InMemoryDocumentVersionRepository;
import org.practice.fundgateway.knowledge.document.InMemoryIndexTaskRepository;
import org.practice.fundgateway.knowledge.document.IndexTaskRepository;
import org.practice.fundgateway.knowledge.document.KnowledgeDocumentApplicationService;
import org.practice.fundgateway.knowledge.document.DocumentIndexApplicationService;
import org.practice.fundgateway.knowledge.document.PostgresDocumentVersionRepository;
import org.practice.fundgateway.knowledge.document.PostgresIndexTaskRepository;
import org.practice.fundgateway.knowledge.embedding.EmbeddingGenerator;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingGenerator;
import org.practice.fundgateway.knowledge.persistence.KnowledgeVectorStore;
import org.practice.fundgateway.knowledge.persistence.PgvectorKnowledgeVectorStore;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 装配控制台第一阶段使用的知识库应用服务。 */
@Configuration
public class KnowledgeConsoleConfiguration {

    /** 创建文档版本内存仓储，后续由 PostgreSQL 实现替换。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "memory")
    public DocumentVersionRepository documentVersionRepository() {
        return new InMemoryDocumentVersionRepository();
    }

    /** 创建索引任务内存仓储，后续由 PostgreSQL 实现替换。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "memory")
    public IndexTaskRepository indexTaskRepository() {
        return new InMemoryIndexTaskRepository();
    }

    /** 正式模式使用 PostgreSQL 保存文档、解析结果和索引任务。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public DocumentVersionRepository postgresDocumentVersionRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresDocumentVersionRepository(jdbcTemplate);
    }

    /** 正式模式使用 PostgreSQL 保存索引任务状态。 */
    @Bean
    @ConditionalOnProperty(name = "console.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
    public IndexTaskRepository postgresIndexTaskRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresIndexTaskRepository(jdbcTemplate);
    }

    /** 创建文档登记、解析和索引任务应用服务。 */
    @Bean
    public KnowledgeDocumentApplicationService knowledgeDocumentApplicationService(
            DocumentVersionRepository documentRepository, IndexTaskRepository taskRepository) {
        return new KnowledgeDocumentApplicationService(documentRepository, taskRepository);
    }

    /** 创建控制台使用的 PostgreSQL 连接模板，不在启动时执行 DDL。 */
    @Bean
    public JdbcTemplate knowledgeJdbcTemplate(
            @Value("${console.jdbc.url}") String url,
            @Value("${console.jdbc.user}") String user,
            @Value("${console.jdbc.password}") String password) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        return new JdbcTemplate(dataSource);
    }

    /** 创建 pgvector 适配器，集合发布由索引应用服务控制。 */
    @Bean
    public KnowledgeVectorStore knowledgeVectorStore(JdbcTemplate jdbcTemplate) {
        return new PgvectorKnowledgeVectorStore(jdbcTemplate);
    }

    /** 延迟加载本地 BGE 模型，避免控制台单元测试启动时要求模型文件。 */
    @Bean
    public EmbeddingGenerator knowledgeEmbeddingGenerator(
            @Value("${console.embedding.model-path}") String modelPath) {
        return new LazyLocalEmbeddingGenerator(java.nio.file.Path.of(modelPath));
    }

    /** 创建正式索引应用服务。 */
    @Bean
    public DocumentIndexApplicationService documentIndexApplicationService(
            DocumentVersionRepository documentRepository, IndexTaskRepository taskRepository,
            EmbeddingGenerator embeddingGenerator, KnowledgeVectorStore vectorStore) {
        return new DocumentIndexApplicationService(documentRepository, taskRepository, embeddingGenerator, vectorStore);
    }

    /** 延迟初始化本地模型的适配器。 */
    private static class LazyLocalEmbeddingGenerator implements EmbeddingGenerator {
        private final java.nio.file.Path modelPath;
        private volatile LocalBgeEmbeddingGenerator delegate;

        /** 保存模型路径，不立即加载模型。 */
        private LazyLocalEmbeddingGenerator(java.nio.file.Path modelPath) {
            this.modelPath = modelPath;
        }

        /** 首次索引时加载模型并生成向量。 */
        @Override
        public float[] embed(String text) throws Exception {
            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = new LocalBgeEmbeddingGenerator(modelPath);
                    }
                }
            }
            return delegate.embed(text);
        }
    }
}
