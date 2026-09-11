package org.practice.fundgateway.integration.contract;

import java.util.StringJoiner;

import org.practice.fundgateway.knowledge.rag.RagEvidenceQueryService;

/** 将已通过 RAG 门禁的证据组装成候选规范抽取提示词。 */
public class CandidateContractPromptBuilder {

    /** 只接受已通过门禁的证据，并要求模型输出固定候选 JSON。 */
    public CandidateContractPrompt build(RagEvidenceQueryService.RagEvidenceResponse response,
                                         String providerId, String interfaceId) {
        if (response == null || response.status() != RagEvidenceQueryService.Status.ACCEPTED
                || response.evidence().isEmpty()) {
            throw new IllegalArgumentException("证据未通过门禁，不能抽取候选规范");
        }
        String system = "你是资方接口规范抽取器，只能使用给定证据。"
                + "不得补造接口地址、字段、类型、必填条件或错误码。"
                + "必须只输出合法 JSON，不要 Markdown，不要解释文字。"
                + "JSON 字段必须为 purpose、endpoint、httpMethod、fields、evidence。"
                + "fields 必须是数组；每项必须包含 name、type、requirement、condition、description、evidence；字段 evidence 必须是单个对象。"
                + "顶层 evidence 必须是证据对象数组，不能是单个对象。"
                + "输出形状示例：{\"purpose\":\"证据明确的用途\",\"endpoint\":\"证据明确的地址\",\"httpMethod\":null,\"fields\":[{\"name\":\"applyAmt\",\"type\":\"BigDecimal\",\"requirement\":\"REQUIRED\",\"condition\":null,\"description\":\"证据明确的说明\",\"evidence\":{\"chunkId\":\"...\",\"documentId\":\"...\",\"documentVersion\":\"...\",\"locator\":\"...\",\"quote\":\"...\"}}],\"evidence\":[{\"chunkId\":\"...\",\"documentId\":\"...\",\"documentVersion\":\"...\",\"locator\":\"...\",\"quote\":\"...\"}]}。"
                + "requirement 只能是 REQUIRED、CONDITIONAL、OPTIONAL。"
                + "每个字段和接口事实都必须引用证据中的 chunkId、documentVersion、locator、quote。"
                + "只抽取证据明确支持的字段，允许候选只包含当前证据覆盖的字段；不要因为没有其他字段就返回证据不足。"
                + "证据不足时输出 {\"error\":\"证据不足\"}。";
        StringJoiner evidence = new StringJoiner("\n\n");
        for (RagEvidenceQueryService.EvidenceCitation citation : response.evidence()) {
            evidence.add("[" + citation.chunkId() + "] 文档版本=" + citation.documentVersion()
                    + "，定位=" + citation.locator() + "，原文=" + citation.content());
        }
        String user = "资方=" + providerId + "，接口=" + interfaceId + "\n"
                + "问题=" + response.question() + "\n证据：\n" + evidence;
        return new CandidateContractPrompt(system, user);
    }

    /** 表示候选规范抽取所需的模型提示词。 */
    public record CandidateContractPrompt(String systemInstruction, String userInput) {
    }
}
