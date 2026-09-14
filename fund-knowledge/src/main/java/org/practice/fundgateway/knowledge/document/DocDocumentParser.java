package org.practice.fundgateway.knowledge.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;

/** 按段落顺序解析传统 DOC 文件。 */
public class DocDocumentParser implements DocumentParser {

    /** 返回 DOC 格式标识。 */
    @Override
    public DocumentFormat format() {
        return DocumentFormat.DOC;
    }

    /** 解析 DOC 段落并保留段落序号和章节路径。 */
    @Override
    public ParsedDocument parse(Path file, String documentId, String version) throws IOException {
        DocumentSource source = DocumentParsingSupport.source(file, documentId, version, "DOC");
        List<DocumentElement> elements = new ArrayList<>();
        String sectionPath = "未命名章节";
        int sequence = 0;
        try (InputStream input = Files.newInputStream(file); HWPFDocument document = new HWPFDocument(input)) {
            Range range = document.getRange();
            for (int index = 0; index < range.numParagraphs(); index++) {
                Paragraph paragraph = range.getParagraph(index);
                String rawText = paragraph.text();
                String cleanedText = DocumentParsingSupport.cleanText(rawText);
                if (cleanedText.isEmpty()) {
                    continue;
                }
                if (cleanedText.matches("^(?:\\d+(?:\\.\\d+)*|【[^】]+】).+")) {
                    sectionPath = cleanedText;
                }
                elements.add(DocumentElement.paragraph(sequence++, sectionPath, rawText, cleanedText));
            }
        }
        return new ParsedDocument(source, elements);
    }
}
