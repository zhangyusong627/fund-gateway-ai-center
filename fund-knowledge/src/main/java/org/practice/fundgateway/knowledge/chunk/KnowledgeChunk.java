package org.practice.fundgateway.knowledge.chunk;

import org.practice.fundgateway.knowledge.document.DocumentSource;

/** 表示一个可生成向量并可反查来源的知识分块。 */
public record KnowledgeChunk(
        String chunkId,
        DocumentSource source,
        String sectionPath,
        String text,
        int firstSequence,
        int lastSequence,
        int tableIndex,
        int rowIndex) {
}
