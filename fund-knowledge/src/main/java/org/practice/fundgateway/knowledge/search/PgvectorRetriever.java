package org.practice.fundgateway.knowledge.search;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/** 使用 PostgreSQL pgvector 执行相似度检索和 Top-K 筛选。 */
public class PgvectorRetriever {

    private final JdbcTemplate jdbcTemplate;

    /** 创建数据库检索器。 */
    public PgvectorRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 由 pgvector 计算余弦距离并返回最相近的候选。 */
    public List<RetrievedChunk> search(String collectionName, float[] queryVector, int topK) {
        return search(collectionName, null, null, queryVector, topK);
    }

    /** 在指定文档版本范围内执行向量检索；文档为空时检索整个集合。 */
    public List<RetrievedChunk> search(String collectionName, String documentId, String documentVersion,
                                       float[] queryVector, int topK) {
        return search(collectionName, documentId, documentVersion, null, queryVector, topK);
    }

    /** 在指定资方和可选文档版本范围内执行向量检索。 */
    public List<RetrievedChunk> search(String collectionName, String documentId, String documentVersion,
                                       String providerId, float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("Top-K 必须大于零");
        }
        String vectorLiteral = toVectorLiteral(queryVector);
        boolean documentScoped = documentId != null && !documentId.isBlank();
        boolean providerScoped = providerId != null && !providerId.isBlank();
        String sql = "SELECT chunk_id, content, document_id, document_version, locator, "
                + "metadata->>'sectionPath' AS section_path, metadata->>'tableIndex' AS table_index, "
                + "metadata->>'rowIndex' AS row_index, 1 - (embedding <=> ?::vector) AS score "
                + "FROM knowledge.knowledge_chunks c JOIN knowledge.rag_collections r ON r.collection_name=c.collection_name "
                + "WHERE c.collection_name = ? AND r.status='PUBLISHED' "
                + (providerScoped ? "AND (c.institution = ? OR (? = 'NYXJ' AND c.institution IN ('synthetic-source','synthetic-provider'))) " : "")
                + (documentScoped ? "AND c.document_id = ? AND c.document_version = ? " : "")
                + "ORDER BY embedding <=> ?::vector LIMIT ?";
        java.util.List<Object> arguments = new java.util.ArrayList<>();
        arguments.add(vectorLiteral);
        arguments.add(collectionName);
        if (providerScoped) {
            arguments.add(providerId);
            arguments.add(providerId);
        }
        if (documentScoped) {
            arguments.add(documentId);
            arguments.add(documentVersion);
        }
        arguments.add(vectorLiteral);
        arguments.add(topK);
        return jdbcTemplate.query(sql,
                (resultSet, rowNumber) -> new RetrievedChunk(
                        resultSet.getString("chunk_id"),
                        resultSet.getString("content"),
                        resultSet.getDouble("score"),
                        resultSet.getString("document_id"),
                        resultSet.getString("document_version"),
                        resultSet.getString("section_path"),
                        integerOrDefault(resultSet.getString("table_index"), -1),
                        integerOrDefault(resultSet.getString("row_index"), -1),
                        resultSet.getString("locator")), arguments.toArray());
    }

    /** 把 Java 向量转换为 pgvector 接受的文本表示。 */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    /** 表示数据库返回的检索候选。 */
    public record RetrievedChunk(
            String chunkId,
            String content,
            double score,
            String documentId,
            String documentVersion,
            String sectionPath,
            int tableIndex,
            int rowIndex,
            String locator) {

        /** 保留旧调用方只关心文本和分数时的构造方式。 */
        public RetrievedChunk(String chunkId, String content, double score) {
            this(chunkId, content, score, null, null, null, -1, -1, null);
        }
    }

    /** 将可空的 JSON 文本数字转换为来源坐标。 */
    private static int integerOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
