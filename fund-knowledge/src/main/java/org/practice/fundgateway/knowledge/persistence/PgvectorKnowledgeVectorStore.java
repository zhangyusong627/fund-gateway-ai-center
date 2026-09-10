package org.practice.fundgateway.knowledge.persistence;

import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.practice.fundgateway.knowledge.embedding.EmbeddingVectorValidator;
import org.springframework.jdbc.core.JdbcTemplate;

/** 使用现有 knowledge_chunks 表保存可追溯的 pgvector 知识分块。 */
public class PgvectorKnowledgeVectorStore implements KnowledgeVectorStore {

    private final JdbcTemplate jdbcTemplate;

    /** 创建 PostgreSQL 向量存储适配器。 */
    public PgvectorKnowledgeVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 注册集合，并拒绝覆盖已有集合的模型配置。 */
    @Override
    public void ensureCollection(String collectionName, String description,
                                 EmbeddingDescriptor descriptor) {
        requireCollectionName(collectionName);
        String sql = "INSERT INTO rag_collections "
                + "(collection_name, description, embedding_provider, embedding_model, "
                + "embedding_dimension, embedding_normalize) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (collection_name) DO UPDATE SET updated_at = now() "
                + "WHERE rag_collections.embedding_provider = EXCLUDED.embedding_provider "
                + "AND rag_collections.embedding_model = EXCLUDED.embedding_model "
                + "AND rag_collections.embedding_dimension = EXCLUDED.embedding_dimension "
                + "AND rag_collections.embedding_normalize = EXCLUDED.embedding_normalize";
        int updated = jdbcTemplate.update(sql, collectionName, description,
                descriptor.provider(), descriptor.model(), descriptor.dimension(), descriptor.normalized());
        if (updated == 0) {
            throw new IllegalStateException("向量集合已存在但模型配置不一致：" + collectionName);
        }
    }

    /** 幂等保存分块，模型配置和来源定位一并落库。 */
    @Override
    public void save(String collectionName, KnowledgeChunk chunk, float[] vector,
                     EmbeddingDescriptor descriptor) {
        requireCollectionName(collectionName);
        EmbeddingVectorValidator.validate(vector, descriptor);
        String sql = "INSERT INTO knowledge_chunks "
                + "(chunk_id, collection_name, document, content, embedding, metadata, document_id, "
                + "document_version, institution, product_code, operation, content_type, source_type, "
                + "block_type, locator, embedding_provider, embedding_model, embedding_dimension, "
                + "embedding_normalize) VALUES (?, ?, ?, ?, ?::vector, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (chunk_id) DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding, "
                + "metadata = EXCLUDED.metadata, updated_at = now() "
                + "WHERE knowledge_chunks.embedding_model = EXCLUDED.embedding_model "
                + "AND knowledge_chunks.embedding_dimension = EXCLUDED.embedding_dimension";
        String locator = chunk.sectionPath() + "#" + chunk.firstSequence() + "-" + chunk.lastSequence();
        jdbcTemplate.update(sql, chunk.chunkId(), collectionName, chunk.sectionPath(), chunk.text(),
                vectorLiteral(vector), metadataJson(chunk), chunk.source().documentId(), chunk.source().version(),
                "synthetic-source", "fund-gateway", chunk.sectionPath(), "text", "document", "knowledge",
                locator, descriptor.provider(), descriptor.model(), descriptor.dimension(), descriptor.normalized());
    }

    /** 将 Java 向量转换为 pgvector 文本字面量。 */
    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    /** 保存来源定位元数据，供后续引用和排查使用。 */
    private String metadataJson(KnowledgeChunk chunk) {
        return "{\"sectionPath\":\"" + jsonEscape(chunk.sectionPath())
                + "\",\"firstSequence\":" + chunk.firstSequence()
                + ",\"lastSequence\":" + chunk.lastSequence()
                + ",\"tableIndex\":" + chunk.tableIndex()
                + ",\"rowIndex\":" + chunk.rowIndex() + "}";
    }

    /** 转义元数据中的 JSON 字符。 */
    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 防止集合名被误用为动态 SQL 标识。 */
    private void requireCollectionName(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("集合名称不能为空");
        }
    }
}
