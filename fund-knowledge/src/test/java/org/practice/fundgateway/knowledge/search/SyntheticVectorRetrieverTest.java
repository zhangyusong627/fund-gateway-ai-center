package org.practice.fundgateway.knowledge.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证合成中英混排、表格文本和流程文本的最小向量检索行为。 */
class SyntheticVectorRetrieverTest {

    private final SyntheticVectorRetriever retriever = new SyntheticVectorRetriever();

    /** 查询向量最相近的契约文本应排在首位。 */
    @Test
    void returnsTopKByCosineSimilarity() {
        List<SyntheticVectorRetriever.VectorDocument> documents = List.of(
                new SyntheticVectorRetriever.VectorDocument("contract", "QPS 上限 20，timeout 3000ms", new float[]{1, 0, 0}),
                new SyntheticVectorRetriever.VectorDocument("table", "字段 | 类型 | 必填", new float[]{0.8f, 0.2f, 0}),
                new SyntheticVectorRetriever.VectorDocument("flow", "申请 → 审批 → 放款", new float[]{0, 1, 0}));

        List<SyntheticVectorRetriever.VectorDocument> result = retriever.search(documents, new float[]{1, 0, 0}, 2);

        assertEquals(List.of("contract", "table"), result.stream().map(SyntheticVectorRetriever.VectorDocument::id).toList());
    }

    /** 向量维度不一致时必须明确失败。 */
    @Test
    void rejectsDifferentDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> SyntheticVectorRetriever.cosineSimilarity(new float[]{1}, new float[]{1, 0}));
    }
}
