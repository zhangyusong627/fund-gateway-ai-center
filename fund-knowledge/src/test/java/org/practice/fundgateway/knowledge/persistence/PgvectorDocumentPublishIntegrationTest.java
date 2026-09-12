package org.practice.fundgateway.knowledge.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.document.DocumentSource;
import org.practice.fundgateway.knowledge.document.IndexTask;
import org.practice.fundgateway.knowledge.document.PostgresIndexTaskRepository;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** 使用真实 pgvector 验证文档级发布事务不会破坏共享集合。 */
@EnabledIfEnvironmentVariable(named = "PGVECTOR_TEST_URL", matches = ".+")
class PgvectorDocumentPublishIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private PgvectorKnowledgeVectorStore vectorStore;
    private String collectionName;
    private String taskDocumentId;

    /** 连接显式指定的本地测试数据库，并创建独立集合名。 */
    @BeforeEach
    void setUp() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(System.getenv("PGVECTOR_TEST_URL"));
        dataSource.setUser(System.getenv().getOrDefault("PGVECTOR_TEST_USER", "fund_demo"));
        dataSource.setPassword(System.getenv().getOrDefault("PGVECTOR_TEST_PASSWORD", "fund_demo_password"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        vectorStore = new PgvectorKnowledgeVectorStore(jdbcTemplate);
        collectionName = "tx-test-" + UUID.randomUUID();
        taskDocumentId = "claim-test-" + UUID.randomUUID();
    }

    /** 删除本测试创建的隔离数据，避免污染本地演示库。 */
    @AfterEach
    void tearDown() {
        if (jdbcTemplate != null && collectionName != null) {
            jdbcTemplate.update("delete from knowledge.knowledge_chunks where collection_name=?", collectionName);
            jdbcTemplate.update("delete from knowledge.rag_collections where collection_name=?", collectionName);
            jdbcTemplate.update("delete from knowledge.knowledge_index_tasks where document_id=?", taskDocumentId);
            jdbcTemplate.update("delete from knowledge.knowledge_documents where document_id=?", taskDocumentId);
        }
    }

    /** 第二个分块失败时应回滚本次文档写入，并保留集合内旧文档。 */
    @Test
    void shouldRollbackOnlyNewDocumentWhenPublishFails() {
        EmbeddingDescriptor descriptor = new EmbeddingDescriptor("test", "test-model", 512, true);
        DocumentSource oldSource = new DocumentSource("old-doc", "v1", Path.of("old.docx"), "old-hash");
        KnowledgeChunk oldChunk = chunk("old-chunk", oldSource);
        vectorStore.ensureCollection(collectionName, "事务测试集合", descriptor);
        vectorStore.save(collectionName, oldChunk, vector(1F), descriptor);

        DocumentSource newSource = new DocumentSource("new-doc", "v1", Path.of("new.docx"), "new-hash");
        List<KnowledgeChunk> newChunks = Arrays.asList(
                chunk("new-chunk-1", newSource), chunk("new-chunk-2", newSource));

        assertThatThrownBy(() -> vectorStore.publishDocument(collectionName, "事务测试集合", newChunks,
                Arrays.asList(vector(2F), new float[] {3F}), descriptor))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(count("old-doc")).isEqualTo(1);
        assertThat(count("new-doc")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select status from knowledge.rag_collections where collection_name=?", String.class,
                collectionName)).isEqualTo("PUBLISHED");
    }

    /** 并发执行同一任务时，数据库必须只允许一个请求抢占成功。 */
    @Test
    void shouldAllowOnlyOneDatabaseClaim() throws Exception {
        jdbcTemplate.update("insert into knowledge.knowledge_documents "
                        + "(document_id, version, file_path, file_sha256, status) values (?, 'v1', ?, ?, 'PARSED')",
                taskDocumentId, "/tmp/" + taskDocumentId + ".docx",
                String.format("%064x", Math.abs(taskDocumentId.hashCode())));
        PostgresIndexTaskRepository repository = new PostgresIndexTaskRepository(jdbcTemplate);
        IndexTask created = repository.create(IndexTask.created(taskDocumentId, "v1"));
        IndexTask duplicate = repository.create(IndexTask.created(taskDocumentId, "v1"));
        assertThat(duplicate.taskId()).isEqualTo(created.taskId());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return repository.claim(created.taskId()).isPresent();
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return repository.claim(created.taskId()).isPresent();
            });
            start.countDown();
            assertThat(Arrays.asList(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 构造带稳定来源定位的测试分块。 */
    private KnowledgeChunk chunk(String chunkId, DocumentSource source) {
        return new KnowledgeChunk(chunkId, source, "测试章节", "测试内容", 1, 1, -1, -1);
    }

    /** 构造符合当前 pgvector 维度的测试向量。 */
    private float[] vector(float firstValue) {
        float[] vector = new float[512];
        vector[0] = firstValue;
        return vector;
    }

    /** 统计指定文档在测试集合中的向量数量。 */
    private int count(String documentId) {
        return jdbcTemplate.queryForObject("select count(*) from knowledge.knowledge_chunks "
                + "where collection_name=? and document_id=?", Integer.class, collectionName, documentId);
    }
}
