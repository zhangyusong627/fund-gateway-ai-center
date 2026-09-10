package org.practice.fundgateway.knowledge.rag;

import java.util.List;
import java.util.Set;

import org.practice.fundgateway.knowledge.search.EvidenceAcceptanceGate;
import org.practice.fundgateway.knowledge.search.HybridPgvectorRetriever;
import org.practice.fundgateway.knowledge.search.PgvectorRetriever;
import org.springframework.jdbc.core.JdbcTemplate;

/** 编排查询、混合检索和证据门禁，只向下游暴露已接受的证据。 */
public class RagEvidenceQueryService {

    private final HybridPgvectorRetriever retriever;
    private final EvidenceAcceptanceGate acceptanceGate;

    /** 创建 RAG 证据查询服务。 */
    public RagEvidenceQueryService(JdbcTemplate jdbcTemplate) {
        this.retriever = new HybridPgvectorRetriever(jdbcTemplate);
        this.acceptanceGate = new EvidenceAcceptanceGate();
    }

    /** 查询可供后续生成使用的证据，不在本服务内调用大模型。 */
    public RagEvidenceResponse query(String collectionName, String question, float[] queryVector,
                                     Set<String> requiredKeywords, int topK) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        List<HybridPgvectorRetriever.HybridRetrievedChunk> candidates =
                retriever.search(collectionName, queryVector, requiredKeywords, topK);
        EvidenceAcceptanceGate.AcceptanceResult acceptance =
                acceptanceGate.evaluate(candidates, requiredKeywords);
        if (!acceptance.accepted()) {
            return RagEvidenceResponse.insufficient(question, acceptance.missingKeywords());
        }
        List<EvidenceCitation> evidence = candidates.stream()
                .map(HybridPgvectorRetriever.HybridRetrievedChunk::chunk)
                .map(EvidenceCitation::from)
                .toList();
        return RagEvidenceResponse.accepted(question, evidence);
    }

    /** 表示 RAG 证据是否可以交给后续答案生成环节。 */
    public record RagEvidenceResponse(
            Status status,
            String question,
            List<EvidenceCitation> evidence,
            Set<String> missingKeywords) {

        /** 创建已通过门禁的响应。 */
        public static RagEvidenceResponse accepted(String question, List<EvidenceCitation> evidence) {
            return new RagEvidenceResponse(Status.ACCEPTED, question, List.copyOf(evidence), Set.of());
        }

        /** 创建证据不足响应，阻断后续生成。 */
        public static RagEvidenceResponse insufficient(String question, Set<String> missingKeywords) {
            return new RagEvidenceResponse(Status.INSUFFICIENT_EVIDENCE, question, List.of(),
                    Set.copyOf(missingKeywords));
        }
    }

    /** 证据引用，保留生成答案所需的原文和来源定位。 */
    public record EvidenceCitation(
            String chunkId,
            String content,
            double score,
            String documentId,
            String documentVersion,
            String sectionPath,
            int tableIndex,
            int rowIndex,
            String locator) {

        /** 将数据库检索结果映射成稳定的 RAG 引用对象。 */
        private static EvidenceCitation from(PgvectorRetriever.RetrievedChunk chunk) {
            return new EvidenceCitation(chunk.chunkId(), chunk.content(), chunk.score(),
                    chunk.documentId(), chunk.documentVersion(), chunk.sectionPath(),
                    chunk.tableIndex(), chunk.rowIndex(), chunk.locator());
        }
    }

    /** RAG 证据门禁状态。 */
    public enum Status {
        ACCEPTED,
        INSUFFICIENT_EVIDENCE
    }
}
