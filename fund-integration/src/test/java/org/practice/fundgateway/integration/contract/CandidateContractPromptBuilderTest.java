package org.practice.fundgateway.integration.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.knowledge.rag.RagEvidenceQueryService;

/** 验证候选规范抽取提示词只接受已通过门禁的 RAG 证据。 */
class CandidateContractPromptBuilderTest {

    /** 已接受证据会进入固定 JSON 输出约束。 */
    @Test
    void shouldBuildConstrainedPrompt() {
        RagEvidenceQueryService.EvidenceCitation citation = citation();
        RagEvidenceQueryService.RagEvidenceResponse response =
                RagEvidenceQueryService.RagEvidenceResponse.accepted("申请金额字段是什么", List.of(citation));

        CandidateContractPromptBuilder.CandidateContractPrompt prompt =
                new CandidateContractPromptBuilder().build(response, "synthetic-provider", "credit-application");

        assertTrue(prompt.systemInstruction().contains("只输出合法 JSON"));
        assertTrue(prompt.userInput().contains("doc:v1:117"));
    }

    /** 证据不足时不能触发模型抽取。 */
    @Test
    void shouldRejectInsufficientEvidence() {
        RagEvidenceQueryService.RagEvidenceResponse response =
                RagEvidenceQueryService.RagEvidenceResponse.insufficient("未知字段", Set.of("riskScore"));

        assertThrows(IllegalArgumentException.class,
                () -> new CandidateContractPromptBuilder().build(response,
                        "synthetic-provider", "credit-application"));
    }

    /** 创建合成 RAG 引用。 */
    private RagEvidenceQueryService.EvidenceCitation citation() {
        return new RagEvidenceQueryService.EvidenceCitation("doc:v1:117", "applyAmt | BigDecimal | 必填",
                0.9D, "synthetic-doc", "v1", "4.2 授信申请", 1, 117,
                "4.2 授信申请#117-117");
    }
}
