package org.practice.fundgateway.knowledge.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 验证证据接受门禁不会把无来源或缺关键词的候选当成有效证据。 */
class EvidenceAcceptanceGateTest {

    /** 有完整来源并覆盖关键词时才接受。 */
    @Test
    void shouldAcceptCitedEvidenceWithRequiredKeywords() {
        var gate = new EvidenceAcceptanceGate();
        var chunk = citedChunk("applyAmt | BigDecimal | Y");

        var result = gate.evaluate(List.of(new HybridPgvectorRetriever.HybridRetrievedChunk(
                chunk, 1D, 0.8D)), Set.of("applyAmt", "BigDecimal"));

        assertTrue(result.accepted());
    }

    /** 缺少来源或关键词时必须拒绝。 */
    @Test
    void shouldRejectUnsupportedEvidence() {
        var gate = new EvidenceAcceptanceGate();
        var chunk = new PgvectorRetriever.RetrievedChunk("id", "相似但无目标字段", 0.8D);

        var result = gate.evaluate(List.of(new HybridPgvectorRetriever.HybridRetrievedChunk(
                chunk, 0D, 0.5D)), Set.of("riskScore"));

        assertFalse(result.accepted());
        assertTrue(result.missingKeywords().contains("riskScore"));
    }

    /** 创建包含来源定位的候选。 */
    private PgvectorRetriever.RetrievedChunk citedChunk(String content) {
        return new PgvectorRetriever.RetrievedChunk("id", content, 0.8D,
                "doc", "v1", "授信申请", 1, 2, "授信申请#1-2");
    }
}
