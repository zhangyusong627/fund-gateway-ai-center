package org.practice.fundgateway.knowledge.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 固定验证证据门禁的通过、拒答、无来源和非法输入分支。 */
class EvidenceAcceptanceFixedSetTest {

    /** 固定集的通过与拒答结果必须保持可复算，不把候选召回当成有效证据。 */
    @Test
    void fixedEvidenceGateSetHasExpectedOutcomes() {
        EvidenceAcceptanceGate gate = new EvidenceAcceptanceGate();

        List<Boolean> outcomes = List.of(
                gate.evaluate(List.of(cited("applyAmt | BigDecimal | 必填")), Set.of("applyAmt", "BigDecimal"))
                        .accepted(),
                gate.evaluate(List.of(cited("相似字段，不包含目标字段")), Set.of("riskScore")).accepted(),
                gate.evaluate(List.of(uncited("applyAmt | BigDecimal | 必填")), Set.of("applyAmt")).accepted());

        assertEquals(List.of(true, false, false), outcomes);
    }

    /** 固定集中的无证据题必须报告缺失关键词，不能仅因有相似候选而通过。 */
    @Test
    void fixedUnsupportedCaseReportsMissingKeyword() {
        EvidenceAcceptanceGate.AcceptanceResult result = new EvidenceAcceptanceGate().evaluate(
                List.of(cited("授信申请接口说明")), Set.of("riskScore"));

        assertEquals(Set.of("riskScore"), result.missingKeywords());
        assertTrue(result.hasCitation());
    }

    /** 空关键词集合属于调用方参数错误，必须明确失败而不是放行全部候选。 */
    @Test
    void rejectsEmptyRequiredKeywords() {
        assertThrows(IllegalArgumentException.class,
                () -> new EvidenceAcceptanceGate().evaluate(List.of(), Set.of()));
    }

    /** 创建带完整来源定位的固定候选。 */
    private HybridPgvectorRetriever.HybridRetrievedChunk cited(String content) {
        return new HybridPgvectorRetriever.HybridRetrievedChunk(
                new PgvectorRetriever.RetrievedChunk("fixed-chunk", content, 0.9D,
                        "shengheng-api", "v1", "授信申请", 1, 1, "授信申请#1-1"),
                1D, 0.9D);
    }

    /** 创建没有来源定位的固定候选。 */
    private HybridPgvectorRetriever.HybridRetrievedChunk uncited(String content) {
        return new HybridPgvectorRetriever.HybridRetrievedChunk(
                new PgvectorRetriever.RetrievedChunk("fixed-chunk", content, 0.9D), 1D, 0.9D);
    }
}
