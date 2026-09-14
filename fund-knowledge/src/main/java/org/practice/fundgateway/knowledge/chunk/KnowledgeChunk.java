package org.practice.fundgateway.knowledge.chunk;

import org.practice.fundgateway.knowledge.document.DocumentSource;
import org.practice.fundgateway.knowledge.document.DocumentLocator;

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

    /** 生成与解析格式匹配的稳定来源定位。 */
    public String locator() {
        return DocumentLocator.from(this).value();
    }
}
