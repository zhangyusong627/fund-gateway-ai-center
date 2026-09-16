package org.practice.fundgateway.knowledge.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** 按页解析可搜索文本 PDF，不承担 OCR 或复杂表格还原。 */
public class PdfDocumentParser implements DocumentParser {

    /** 返回 PDF 格式标识。 */
    @Override
    public DocumentFormat format() {
        return DocumentFormat.PDF;
    }

    /** 将每页文本输出为一个可追溯的文档元素。 */
    @Override
    public ParsedDocument parse(Path file, String documentId, String version) throws IOException {
        DocumentSource source = DocumentParsingSupport.source(file, documentId, version, "PDF");
        List<DocumentElement> elements = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(file))) {
            if (document.isEncrypted()) {
                throw new IOException("PDF 文件已加密，无法解析");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String rawText = stripper.getText(document);
                List<String> blocks = splitIntoBlocks(rawText, 1200);
                for (int block = 0; block < blocks.size(); block++) {
                    String cleanedText = blocks.get(block);
                    elements.add(DocumentElement.paragraph(elements.size(),
                            "PDF 第" + page + "页·文本块" + (block + 1), cleanedText, cleanedText));
                }
            }
        }
        if (elements.isEmpty()) {
            throw new IOException("PDF 未提取到可搜索文本，不支持扫描件或空白 PDF");
        }
        return new ParsedDocument(source, elements);
    }

    /** 将单页文本按自然换行和最大字符数拆成适合检索的文本块。 */
    static List<String> splitIntoBlocks(String rawText, int maxCharacters) {
        if (maxCharacters < 1) {
            throw new IllegalArgumentException("PDF 文本块上限必须大于零");
        }
        List<String> blocks = new ArrayList<>();
        StringJoiner current = new StringJoiner(" ");
        for (String line : rawText.split("\\R")) {
            String cleanedLine = DocumentParsingSupport.cleanText(line);
            if (cleanedLine.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + cleanedLine.length() + 1 > maxCharacters) {
                blocks.add(current.toString());
                current = new StringJoiner(" ");
            }
            if (cleanedLine.length() > maxCharacters) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                    current = new StringJoiner(" ");
                }
                for (int start = 0; start < cleanedLine.length(); start += maxCharacters) {
                    blocks.add(cleanedLine.substring(start, Math.min(start + maxCharacters, cleanedLine.length())));
                }
            } else {
                current.add(cleanedLine);
            }
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return List.copyOf(blocks);
    }
}
