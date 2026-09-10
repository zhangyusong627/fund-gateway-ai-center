package org.practice.fundgateway.knowledge.document;

import java.util.List;

/** 保存文档版本和有序解析结果。 */
public record ParsedDocument(DocumentSource source, List<DocumentElement> elements) {

    /** 防止调用方修改解析结果顺序。 */
    public ParsedDocument {
        elements = List.copyOf(elements);
    }
}
