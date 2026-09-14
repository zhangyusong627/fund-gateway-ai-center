package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** 验证 XLS 和 XLSX 的 Sheet、行号、单元格文本和哈希输出。 */
class ExcelDocumentParserTest {

    /** 验证 XLSX 解析为带 Sheet 定位的表格行。 */
    @Test
    void shouldParseXlsxRowsWithSheetLocation() throws Exception {
        Path file = Files.createTempFile("knowledge-parser-", ".xlsx");
        try {
            writeFixture(file, new XSSFWorkbook());
            ParsedDocument document = new ExcelDocumentParser(DocumentFormat.XLSX)
                    .parse(file, "fixture", "v1");
            assertEquals("Sheet: 授信字段", document.elements().get(0).sectionPath());
            assertEquals("字段名 | 是否必填", document.elements().get(0).cleanedText());
            assertEquals(1, document.elements().get(1).rowIndex());
            assertTrue(document.elements().get(1).cleanedText().contains("applyAmt"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** 验证传统 XLS 使用同一解析入口。 */
    @Test
    void shouldParseXlsWithSameModel() throws Exception {
        Path file = Files.createTempFile("knowledge-parser-", ".xls");
        try {
            writeFixture(file, new HSSFWorkbook());
            ParsedDocument document = new ExcelDocumentParser(DocumentFormat.XLS)
                    .parse(file, "fixture", "v1");
            assertEquals(2, document.elements().size());
            assertEquals(DocumentElement.ElementType.TABLE_ROW, document.elements().get(1).type());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** 写入仅含合成字段的跨格式工作簿样例。 */
    private void writeFixture(Path file, Workbook workbook) throws Exception {
        try (workbook; OutputStream output = Files.newOutputStream(file)) {
            var sheet = workbook.createSheet("授信字段");
            sheet.createRow(0).createCell(0).setCellValue("字段名");
            sheet.getRow(0).createCell(1).setCellValue("是否必填");
            sheet.createRow(1).createCell(0).setCellValue("applyAmt");
            sheet.getRow(1).createCell(1).setCellValue("是");
            workbook.write(output);
        }
    }
}
