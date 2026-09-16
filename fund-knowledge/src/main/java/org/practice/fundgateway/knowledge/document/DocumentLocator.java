package org.practice.fundgateway.knowledge.document;

import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;

/** 表示可展示、可持久化、可用于评测匹配的稳定文档定位符。 */
public record DocumentLocator(DocumentFormat format, String value) {

    /** 根据分片的格式和来源字段生成统一定位符。 */
    public static DocumentLocator from(KnowledgeChunk chunk) {
        DocumentFormat format = DocumentFormat.from(chunk.source().file().getFileName().toString());
        String value = switch (format) {
            case PDF -> chunk.sectionPath();
            case XLS, XLSX -> chunk.sectionPath() + "#行=" + chunk.rowIndex();
            case DOC, DOCX -> chunk.sectionPath() + "#序号=" + chunk.firstSequence() + "-" + chunk.lastSequence();
        };
        return new DocumentLocator(format, value);
    }

    /** 返回面向页面和日志的定位文本。 */
    @Override
    public String toString() {
        return value;
    }
}
