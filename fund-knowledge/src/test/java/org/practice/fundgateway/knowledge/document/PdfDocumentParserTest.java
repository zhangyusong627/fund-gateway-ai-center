package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/** 验证可搜索 PDF 的页级文本和无文本 PDF 的明确失败边界。 */
class PdfDocumentParserTest {

    /** 验证 PDF 页码会进入章节定位。 */
    @Test
    void shouldParseSearchablePdfByPage() throws Exception {
        Path file = Files.createTempFile("knowledge-parser-", ".pdf");
        try {
            writeTextPdf(file);
            ParsedDocument document = new PdfDocumentParser().parse(file, "fixture", "v1");
            assertEquals(1, document.elements().size());
            assertEquals("PDF 第1页", document.elements().getFirst().sectionPath());
            assertEquals("applyAmt is required", document.elements().getFirst().cleanedText());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** 验证没有可提取文本的 PDF 不会被误报为解析成功。 */
    @Test
    void shouldRejectPdfWithoutSearchableText() throws Exception {
        Path file = Files.createTempFile("knowledge-parser-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(file.toFile());
            assertThrows(java.io.IOException.class,
                    () -> new PdfDocumentParser().parse(file, "fixture", "v1"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** 写入带文本的合成 PDF 样例。 */
    private void writeTextPdf(Path file) throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("applyAmt is required");
                stream.endText();
            }
            document.save(file.toFile());
        }
    }
}
