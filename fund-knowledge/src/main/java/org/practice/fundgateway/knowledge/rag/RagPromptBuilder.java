package org.practice.fundgateway.knowledge.rag;

import java.util.StringJoiner;

/** 将已通过证据门禁的检索结果组装成受约束的模型输入。 */
public class RagPromptBuilder {

    /** 构造系统约束和用户问题，不接受证据不足的结果。 */
    public RagPrompt build(RagEvidenceQueryService.RagEvidenceResponse response) {
        if (response == null || response.status()
                != RagEvidenceQueryService.Status.ACCEPTED) {
            throw new IllegalArgumentException("证据未通过门禁，不能组装模型提示词");
        }
        if (response.evidence().isEmpty()) {
            throw new IllegalArgumentException("已接受响应不能没有证据");
        }
        String systemInstruction = "你是一个基于证据回答问题的助手。"
                + "只能使用用户提供的证据，不得补造文档没有声明的事实。"
                + "回答必须使用中文，并严格输出 JSON："
                + "{\"answer\":\"...\",\"citations\":["
                + "{\"chunkId\":\"...\",\"locator\":\"...\",\"quote\":\"...\"}]}。"
                + "每条结论至少引用一个证据；引用的 chunkId 和 locator 必须来自证据列表。"
                + "证据无法支持问题时，answer 必须写“证据不足”，citations 返回空数组。";
        StringJoiner evidenceText = new StringJoiner("\n\n");
        for (RagEvidenceQueryService.EvidenceCitation citation : response.evidence()) {
            evidenceText.add("[证据 " + citation.chunkId() + "]\n"
                    + "来源：文档=" + citation.documentId()
                    + "，版本=" + citation.documentVersion()
                    + "，章节=" + citation.sectionPath()
                    + "，定位=" + citation.locator() + "\n"
                    + "原文：" + citation.content());
        }
        String userInput = "问题：" + response.question() + "\n\n证据列表：\n" + evidenceText;
        return new RagPrompt(systemInstruction, userInput);
    }

    /** 表示待发送给模型的系统约束和用户输入。 */
    public record RagPrompt(String systemInstruction, String userInput) {
    }
}
