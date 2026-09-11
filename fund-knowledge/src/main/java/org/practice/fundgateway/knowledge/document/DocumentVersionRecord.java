package org.practice.fundgateway.knowledge.document;

import java.util.List;

import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;

/** 保存文档登记、解析结果和分片预览。 */
public record DocumentVersionRecord(
        DocumentSource source,
        DocumentIndexStatus status,
        List<DocumentElement> elements,
        List<KnowledgeChunk> chunks) {

    /** 复制集合，避免仓储中的文档结果被外部修改。 */
    public DocumentVersionRecord {
        if (source == null || status == null) {
            throw new IllegalArgumentException("文档来源和状态不能为空");
        }
        elements = elements == null ? List.of() : List.copyOf(elements);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
