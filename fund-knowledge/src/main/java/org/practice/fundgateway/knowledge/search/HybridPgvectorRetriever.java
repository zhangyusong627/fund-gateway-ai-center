package org.practice.fundgateway.knowledge.search;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

/** 将 pgvector 语义分数与可解释的关键词命中分数融合。 */
public class HybridPgvectorRetriever {

    private static final double VECTOR_WEIGHT = 0.7D;
    private static final double KEYWORD_WEIGHT = 0.3D;
    private final JdbcTemplate jdbcTemplate;
    private final PgvectorRetriever vectorRetriever;

    /** 创建混合检索器。 */
    public HybridPgvectorRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorRetriever = new PgvectorRetriever(jdbcTemplate);
    }

    /** 先扩大向量候选集，再按关键词命中重新排序。 */
    public List<HybridRetrievedChunk> search(String collectionName, float[] queryVector,
                                             Set<String> keywords, int topK) {
        return search(collectionName, null, null, queryVector, keywords, topK);
    }

    /** 在可选文档版本范围内完成向量召回和关键词融合排序。 */
    public List<HybridRetrievedChunk> search(String collectionName, String documentId, String documentVersion,
                                             float[] queryVector, Set<String> keywords, int topK) {
        return search(collectionName, documentId, documentVersion, null, queryVector, keywords, topK);
    }

    /** 在指定资方和可选文档版本范围内完成向量召回和关键词融合排序。 */
    public List<HybridRetrievedChunk> search(String collectionName, String documentId, String documentVersion,
                                             String providerId, float[] queryVector, Set<String> keywords, int topK) {
        if (keywords == null || keywords.isEmpty()) {
            throw new IllegalArgumentException("关键词集合不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("Top-K 必须大于零");
        }
        List<PgvectorRetriever.RetrievedChunk> candidates =
                vectorRetriever.search(collectionName, documentId, documentVersion,
                        providerId, queryVector, Math.max(topK * 10, 50));
        Set<String> normalizedKeywords = keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));
        return candidates.stream()
                .map(candidate -> score(candidate, normalizedKeywords))
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore()))
                .limit(topK)
                .toList();
    }

    /** 对列表问题执行有界文档级接口查询，避免无界扫描和上下文膨胀。 */
    public BoundedInterfaceSearchResult searchBoundedInterfaceChunks(String collectionName,
                                                                      String documentId,
                                                                      String documentVersion,
                                                                      int maxResults) {
        return searchBoundedInterfaceChunks(collectionName, documentId, documentVersion, null, maxResults);
    }

    /** 在指定资方范围内执行有界文档级接口查询。 */
    public BoundedInterfaceSearchResult searchBoundedInterfaceChunks(String collectionName,
                                                                      String documentId,
                                                                      String documentVersion,
                                                                      String providerId,
                                                                      int maxResults) {
        if (maxResults < 1) {
            throw new IllegalArgumentException("有界列表查询上限必须大于零");
        }
        boolean documentScoped = documentId != null && !documentId.isBlank();
        boolean providerScoped = providerId != null && !providerId.isBlank();
        String sql = "SELECT chunk_id, content, document_id, document_version, locator, "
                + "metadata->>'sectionPath' AS section_path, metadata->>'tableIndex' AS table_index, "
                + "metadata->>'rowIndex' AS row_index "
                + "FROM knowledge.knowledge_chunks c "
                + "JOIN knowledge.rag_collections r ON r.collection_name=c.collection_name "
                + "WHERE c.collection_name=? AND r.status='PUBLISHED' "
                + (providerScoped ? "AND (c.institution=? OR (?='NYXJ' AND c.institution IN ('synthetic-source','synthetic-provider'))) " : "")
                + (documentScoped ? "AND c.document_id=? AND c.document_version=? " : "")
                + "AND c.content ~* '交易码[：:]\\s*[A-Za-z][A-Za-z0-9]+XJ' "
                + "ORDER BY c.document_id, c.document_version, c.chunk_id LIMIT ?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(collectionName);
        if (providerScoped) {
            arguments.add(providerId);
            arguments.add(providerId);
        }
        if (documentScoped) {
            arguments.add(documentId);
            arguments.add(documentVersion);
        }
        arguments.add(maxResults + 1);
        List<HybridRetrievedChunk> rows = jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            PgvectorRetriever.RetrievedChunk chunk = new PgvectorRetriever.RetrievedChunk(
                    resultSet.getString("chunk_id"), resultSet.getString("content"), 1.0D,
                    resultSet.getString("document_id"), resultSet.getString("document_version"),
                    resultSet.getString("section_path"), integerOrDefault(resultSet.getString("table_index")),
                    integerOrDefault(resultSet.getString("row_index")), resultSet.getString("locator"));
            return new HybridRetrievedChunk(chunk, 1.0D, 1.0D);
        }, arguments.toArray());
        boolean truncated = rows.size() > maxResults;
        List<HybridRetrievedChunk> bounded = truncated ? rows.subList(0, maxResults) : rows;
        return new BoundedInterfaceSearchResult(List.copyOf(bounded), rows.size(), truncated);
    }

    /** 有界列表查询结果；matchedCount 在截断时表示至少命中的数量。 */
    public record BoundedInterfaceSearchResult(List<HybridRetrievedChunk> candidates,
                                               int matchedCount,
                                               boolean truncated) {
    }

    /**
     * 为具体接口问题补召回“关键词明确命中且确实是接口定义”的分片。
     * 这一步避免向量相似度把目标接口挤出 Top-K；结果仍在文档和已发布集合范围内。
     */
    public List<HybridRetrievedChunk> searchKeywordInterfaceChunks(String collectionName,
                                                                     String documentId,
                                                                     String documentVersion,
                                                                     Set<String> keywords) {
        return searchKeywordInterfaceChunks(collectionName, documentId, documentVersion, null, keywords);
    }

    /** 在指定资方范围内补召回关键词明确命中的接口分片。 */
    public List<HybridRetrievedChunk> searchKeywordInterfaceChunks(String collectionName,
                                                                     String documentId,
                                                                     String documentVersion,
                                                                     String providerId,
                                                                     Set<String> keywords) {
        List<String> normalized = keywords == null ? List.of() : keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        boolean documentScoped = documentId != null && !documentId.isBlank();
        boolean providerScoped = providerId != null && !providerId.isBlank();
        StringBuilder sql = new StringBuilder("SELECT chunk_id, content, document_id, document_version, locator, "
                + "metadata->>'sectionPath' AS section_path, metadata->>'tableIndex' AS table_index, "
                + "metadata->>'rowIndex' AS row_index FROM knowledge.knowledge_chunks c "
                + "JOIN knowledge.rag_collections r ON r.collection_name=c.collection_name "
                + "WHERE c.collection_name=? AND r.status='PUBLISHED' ");
        List<Object> arguments = new ArrayList<>();
        arguments.add(collectionName);
        if (providerScoped) {
            sql.append("AND (c.institution=? OR (?='NYXJ' AND c.institution IN ('synthetic-source','synthetic-provider'))) ");
            arguments.add(providerId);
            arguments.add(providerId);
        }
        if (documentScoped) {
            sql.append("AND c.document_id=? AND c.document_version=? ");
            arguments.add(documentId);
            arguments.add(documentVersion);
        }
        sql.append("AND c.content ~* '交易码[：:]\\s*[A-Za-z][A-Za-z0-9]+XJ' ");
        for (String ignored : normalized) {
            sql.append("AND c.content ILIKE ? ");
            arguments.add("%" + ignored + "%");
        }
        sql.append("ORDER BY c.chunk_id");
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNumber) -> {
            PgvectorRetriever.RetrievedChunk chunk = new PgvectorRetriever.RetrievedChunk(
                    resultSet.getString("chunk_id"), resultSet.getString("content"), 1.0D,
                    resultSet.getString("document_id"), resultSet.getString("document_version"),
                    resultSet.getString("section_path"), integerOrDefault(resultSet.getString("table_index")),
                    integerOrDefault(resultSet.getString("row_index")), resultSet.getString("locator"));
            return new HybridRetrievedChunk(chunk, 1.0D, 1.0D);
        }, arguments.toArray());
    }

    /** 计算关键词命中比例，并与向量分数加权。 */
    private HybridRetrievedChunk score(PgvectorRetriever.RetrievedChunk candidate,
                                       Set<String> keywords) {
        String content = candidate.content().toLowerCase();
        long matched = keywords.stream().filter(content::contains).count();
        double keywordScore = keywords.isEmpty() ? 0D : (double) matched / keywords.size();
        double finalScore = VECTOR_WEIGHT * candidate.score() + KEYWORD_WEIGHT * keywordScore;
        return new HybridRetrievedChunk(candidate, keywordScore, finalScore);
    }

    private static int integerOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /** 表示混合排序后的证据及两个可解释分数。 */
    public record HybridRetrievedChunk(
            PgvectorRetriever.RetrievedChunk chunk,
            double keywordScore,
            double finalScore) {
    }
}
