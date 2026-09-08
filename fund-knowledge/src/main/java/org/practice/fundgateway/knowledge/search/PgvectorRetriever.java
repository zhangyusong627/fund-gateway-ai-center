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
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("Top-K 必须大于零");
        }
        String vectorLiteral = toVectorLiteral(queryVector);
        String sql = "SELECT chunk_id, content, 1 - (embedding <=> ?::vector) AS score "
                + "FROM knowledge_chunks WHERE collection_name = ? "
                + "ORDER BY embedding <=> ?::vector LIMIT ?";
        return jdbcTemplate.query(sql,
                (resultSet, rowNumber) -> new RetrievedChunk(
                        resultSet.getString("chunk_id"),
                        resultSet.getString("content"),
                        resultSet.getDouble("score")),
                vectorLiteral, collectionName, vectorLiteral, topK);
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
    public record RetrievedChunk(String chunkId, String content, double score) {
    }
}
