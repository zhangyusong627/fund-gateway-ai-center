package org.practice.fundgateway.knowledge.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.practice.fundgateway.knowledge.document.DocumentElement;
import org.practice.fundgateway.knowledge.document.ParsedDocument;

/** 将解析元素组织成保留章节和表格来源的检索分块。 */
public class DocumentChunker {

    /** 按章节组织段落，表格按表头和每行形成独立证据块。 */
    public List<KnowledgeChunk> chunk(ParsedDocument document) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<DocumentElement> paragraphBuffer = new ArrayList<>();
        String currentSection = null;
        String currentTableHeader = null;
        int currentTableIndex = -1;

        for (DocumentElement element : document.elements()) {
            if (element.type() == DocumentElement.ElementType.TABLE_ROW) {
                flushParagraphs(document, paragraphBuffer, chunks);
                paragraphBuffer.clear();
                if (element.tableIndex() != currentTableIndex) {
                    currentTableIndex = element.tableIndex();
                    currentTableHeader = null;
                }
                if (element.rowIndex() == 0) {
                    currentTableHeader = element.cleanedText();
                }
                String text = element.rowIndex() == 0 || currentTableHeader == null
                        ? element.cleanedText()
                        : currentTableHeader + "；当前行：" + element.cleanedText();
                chunks.add(new KnowledgeChunk(
                        chunkId(document, element.sequence()), document.source(),
                        element.sectionPath(), text, element.sequence(), element.sequence(),
                        element.tableIndex(), element.rowIndex()));
                continue;
            }
            if (currentSection != null && !currentSection.equals(element.sectionPath())) {
                flushParagraphs(document, paragraphBuffer, chunks);
                paragraphBuffer.clear();
            }
            currentSection = element.sectionPath();
            paragraphBuffer.add(element);
        }
        flushParagraphs(document, paragraphBuffer, chunks);
        return List.copyOf(chunks);
    }

    private void flushParagraphs(ParsedDocument document,
                                 List<DocumentElement> elements,
                                 List<KnowledgeChunk> chunks) {
        if (elements.isEmpty()) {
            return;
        }
        StringJoiner text = new StringJoiner(" ");
        for (DocumentElement element : elements) {
            text.add(element.cleanedText());
        }
        DocumentElement first = elements.get(0);
        DocumentElement last = elements.get(elements.size() - 1);
        chunks.add(new KnowledgeChunk(
                chunkId(document, first.sequence()), document.source(), first.sectionPath(),
                text.toString(), first.sequence(), last.sequence(), -1, -1));
    }

    private String chunkId(ParsedDocument document, int sequence) {
        return document.source().documentId() + ":" + document.source().version() + ":" + sequence;
    }
}
