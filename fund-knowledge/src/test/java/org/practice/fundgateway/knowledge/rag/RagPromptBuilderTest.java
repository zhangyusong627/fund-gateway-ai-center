package org.practice.fundgateway.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 验证模型提示词只能由已通过门禁的证据生成。 */
class RagPromptBuilderTest {

    /** 已接受证据必须携带来源和 JSON 输出约束。 */
    @Test
    void shouldBuildConstrainedPrompt() {
        var citation = new RagEvidenceQueryService.EvidenceCitation(
                "chunk-117", "applyAmt | BigDecimal | Y", 0.8D,
                "doc", "v1", "授信申请", 6, 23, "授信申请#117-117");
        var response = RagEvidenceQueryService.RagEvidenceResponse.accepted(
                "applyAmt 是否必填？", List.of(citation));

        var prompt = new RagPromptBuilder().build(response);

        assertTrue(prompt.systemInstruction().contains("严格输出 JSON"));
        assertTrue(prompt.userInput().contains("chunk-117"));
        assertTrue(prompt.userInput().contains("授信申请#117-117"));
    }

    /** 证据不足时必须在组装提示词前停止。 */
    @Test
    void shouldRejectInsufficientEvidence() {
        var response = RagEvidenceQueryService.RagEvidenceResponse.insufficient(
                "riskScore 是否存在？", Set.of("riskScore"));

        assertThrows(IllegalArgumentException.class,
                () -> new RagPromptBuilder().build(response));
    }
}
