package org.practice.fundgateway.knowledge.search;

import java.util.Comparator;
import java.util.List;

/** 使用合成向量演示最小余弦相似度检索，不连接外部向量库。 */
public class SyntheticVectorRetriever {

    /** 按余弦相似度返回前 k 个文档。 */
    public List<VectorDocument> search(List<VectorDocument> documents, float[] query, int topK) {
        return documents.stream()
                .map(document -> document.withScore(cosineSimilarity(document.vector(), query)))
                .sorted(Comparator.comparing(VectorDocument::score).reversed())
                .limit(topK)
                .toList();
    }

    /** 计算两个向量的余弦相似度。 */
    static double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** 保存文档文本、合成向量和检索得分。 */
    public record VectorDocument(String id, String text, float[] vector, double score) {

        /** 创建尚未计算得分的文档。 */
        public VectorDocument(String id, String text, float[] vector) {
            this(id, text, vector, 0);
        }

        /** 创建带检索得分的副本。 */
        private VectorDocument withScore(double value) {
            return new VectorDocument(id, text, vector, value);
        }
    }
}
