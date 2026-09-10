package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.practice.fundgateway.knowledge.chunk.DocumentChunker;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;

/** 验证 DOCX 顺序解析、来源定位、清洗和表格证据分块。 */
class DocxDocumentParserTest {

    /** 验证段落、表格顺序以及表头会进入数据行证据块。 */
    @Test
    void shouldPreserveOrderAndTableContext() throws Exception {
        Path file = Files.createTempFile("knowledge-parser-", ".docx");
        try {
            writeFixture(file);
            ParsedDocument document = new DocxDocumentParser().parse(file, "fixture", "v1");

            assertEquals("4.2 授信申请", document.elements().get(0).sectionPath());
            assertEquals("applyAmt 金额 必填；申请金额不能为负数",
                    document.elements().get(1).cleanedText());
            assertEquals(DocumentElement.ElementType.TABLE_ROW,
                    document.elements().get(2).type());

            List<KnowledgeChunk> chunks = new DocumentChunker().chunk(document);
            assertTrue(chunks.stream().anyMatch(chunk ->
                    chunk.text().contains("字段名 | 是否必填")
                            && chunk.text().contains("applyAmt | 是")));
            assertEquals(64, document.source().fileSha256().length());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** 创建只含学习样例的 DOCX，不使用真实机构字段。 */
    private void writeFixture(Path file) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             OutputStream output = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("4.2 授信申请");
            document.createParagraph().createRun().setText("applyAmt\t金额\n必填；申请金额不能为负数");
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("字段名");
            table.getRow(0).getCell(1).setText("是否必填");
            table.getRow(1).getCell(0).setText("applyAmt");
            table.getRow(1).getCell(1).setText("是");
            document.write(output);
        }
    }
}
