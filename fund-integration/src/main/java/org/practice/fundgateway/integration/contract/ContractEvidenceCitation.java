package org.practice.fundgateway.integration.contract;

/** 保存候选事实对应的 RAG 原文证据定位。 */
public record ContractEvidenceCitation(
        String chunkId,
        String documentId,
        String documentVersion,
        String locator,
        String quote) {
}
