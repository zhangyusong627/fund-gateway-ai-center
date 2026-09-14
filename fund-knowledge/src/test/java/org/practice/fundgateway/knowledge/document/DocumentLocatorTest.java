package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;

/** 验证不同格式生成可读且稳定的 locator。 */
class DocumentLocatorTest {

    /** 验证 PDF 使用页级文本块定位。 */
    @Test
    void shouldLocatePdfTextBlock() {
        KnowledgeChunk chunk = chunk("guide.pdf", "PDF 第2页", 3, 3, -1, -1);
        assertEquals("PDF 第2页#文本块=3-3", chunk.locator());
    }

    /** 验证 Excel 使用 Sheet 和原始行号定位。 */
    @Test
    void shouldLocateExcelRow() {
        KnowledgeChunk chunk = chunk("guide.xlsx", "Sheet: 字段", 1, 1, 0, 8);
        assertEquals("Sheet: 字段#行=8", chunk.locator());
    }

    /** 验证 DOCX 继续使用章节和序号范围定位。 */
    @Test
    void shouldLocateDocxSequenceRange() {
        KnowledgeChunk chunk = chunk("guide.docx", "授信申请", 2, 4, -1, -1);
        assertEquals("授信申请#序号=2-4", chunk.locator());
    }

    /** 创建格式定位测试用分片。 */
    private KnowledgeChunk chunk(String filename, String section, int first, int last,
                                 int table, int row) {
        DocumentSource source = new DocumentSource("guide", "v1", Path.of(filename), "hash");
        return new KnowledgeChunk("guide:v1:1", source, section, "text", first, last, table, row);
    }
}
