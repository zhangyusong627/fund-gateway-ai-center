package org.practice.fundgateway.knowledge.search;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

/** 将 pgvector 语义分数与可解释的关键词命中分数融合。 */
public class HybridPgvectorRetriever {

    private static final double VECTOR_WEIGHT = 0.7D;
    private static final double KEYWORD_WEIGHT = 0.3D;
    private final PgvectorRetriever vectorRetriever;

    /** 创建混合检索器。 */
    public HybridPgvectorRetriever(JdbcTemplate jdbcTemplate) {
        this.vectorRetriever = new PgvectorRetriever(jdbcTemplate);
    }

    /** 先扩大向量候选集，再按关键词命中重新排序。 */
    public List<HybridRetrievedChunk> search(String collectionName, float[] queryVector,
                                             Set<String> keywords, int topK) {
        if (keywords == null || keywords.isEmpty()) {
            throw new IllegalArgumentException("关键词集合不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("Top-K 必须大于零");
        }
        List<PgvectorRetriever.RetrievedChunk> candidates =
                vectorRetriever.search(collectionName, queryVector, Math.max(topK * 10, 50));
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

    /** 计算关键词命中比例，并与向量分数加权。 */
    private HybridRetrievedChunk score(PgvectorRetriever.RetrievedChunk candidate,
                                       Set<String> keywords) {
        String content = candidate.content().toLowerCase();
        long matched = keywords.stream().filter(content::contains).count();
        double keywordScore = keywords.isEmpty() ? 0D : (double) matched / keywords.size();
        double finalScore = VECTOR_WEIGHT * candidate.score() + KEYWORD_WEIGHT * keywordScore;
        return new HybridRetrievedChunk(candidate, keywordScore, finalScore);
    }

    /** 表示混合排序后的证据及两个可解释分数。 */
    public record HybridRetrievedChunk(
            PgvectorRetriever.RetrievedChunk chunk,
            double keywordScore,
            double finalScore) {
    }
}
