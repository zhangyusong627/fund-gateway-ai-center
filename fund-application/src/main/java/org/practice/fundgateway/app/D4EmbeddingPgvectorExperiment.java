package org.practice.fundgateway.app;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingModel;
import org.practice.fundgateway.knowledge.search.PgvectorRetriever;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** D4 端到端验证：本地 BGE 向量写入 pgvector 并执行在线 Top-K 检索。 */
@Component
@Profile("d4-real")
public class D4EmbeddingPgvectorExperiment implements CommandLineRunner {

    private static final String COLLECTION = "fund_integration_pgvector_demo";
    private static final Path MODEL = Path.of("..", "models", "bge-small-zh-v1.5");
    private static final Path EVIDENCE = Path.of("..", "docs", "learning", "D4-real-call-" + System.currentTimeMillis());

    @Override
    public void run(String... args) {
        try (LocalBgeEmbeddingModel embedding = new LocalBgeEmbeddingModel(MODEL)) {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
            dataSource.setUser("fund_demo");
            dataSource.setPassword("fund_demo_password");
            try (Connection connection = dataSource.getConnection()) {
                insert(connection, embedding, "credit-apply", "授信申请接口的申请金额字段");
                insert(connection, embedding, "credit-apply", "授信申请接口的申请期限字段");
                insert(connection, embedding, "repayment-query", "还款查询接口的还款日期字段");
            }
            float[] query = embedding.embed("授信申请需要填写申请金额");
            List<PgvectorRetriever.RetrievedChunk> results =
                    new PgvectorRetriever(new JdbcTemplate(dataSource)).search(COLLECTION, query, 2);
            results.forEach(result -> System.out.println(
                    result.chunkId() + " score=" + result.score() + " content=" + result.content()));
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("input.txt"), "授信申请需要填写申请金额\nmodel=BAAI/bge-small-zh-v1.5\ndimension=512\n");
            Files.writeString(EVIDENCE.resolve("results.txt"), results.toString());
            Files.writeString(EVIDENCE.resolve("status.txt"), "embedding=success\npgvector=success\ntopK=2\n");
            System.out.println("D4 evidence=" + EVIDENCE);
        } catch (Exception exception) {
            throw new IllegalStateException("D4 embedding retrieval failed", exception);
        }
    }

    /** 将一条合成文档及其真实向量写入知识片段表。 */
    private static void insert(Connection connection, LocalBgeEmbeddingModel embedding,
                               String operation, String content) throws Exception {
        float[] vector = embedding.embed(content);
        String sql = "INSERT INTO knowledge_chunks (chunk_id, collection_name, document, content, embedding, "
                + "document_id, document_version, institution, product_code, operation, content_type, source_type, "
                + "block_type, locator, embedding_provider, embedding_model, embedding_dimension) "
                + "VALUES (?, ?, ?, ?, ?::vector, ?, 'v1', 'synthetic-provider', 'synthetic-product', ?, "
                + "'field', 'synthetic', 'text', ?, 'local', 'BAAI/bge-small-zh-v1.5', 512) "
                + "ON CONFLICT (chunk_id) DO UPDATE SET embedding = EXCLUDED.embedding, content = EXCLUDED.content";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "d4-" + operation + "-" + Integer.toHexString(content.hashCode()));
            statement.setString(2, COLLECTION);
            statement.setString(3, operation);
            statement.setString(4, content);
            statement.setString(5, vectorLiteral(vector));
            statement.setString(6, operation);
            statement.setString(7, operation + ":" + content);
            statement.setString(8, operation + ":" + content);
            statement.executeUpdate();
        }
    }

    /** 将向量转换为 pgvector 文本字面量。 */
    private static String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) builder.append(',');
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }
}
