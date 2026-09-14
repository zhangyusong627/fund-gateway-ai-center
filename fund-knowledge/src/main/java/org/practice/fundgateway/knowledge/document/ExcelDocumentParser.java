package org.practice.fundgateway.knowledge.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** 按 Sheet 和行顺序解析 XLS/XLSX，单元格范围保留在表格行文本中。 */
public class ExcelDocumentParser implements DocumentParser {

    private final DocumentFormat format;

    /** 创建一个同时可用于 XLS 或 XLSX 的 Excel 解析器。 */
    public ExcelDocumentParser(DocumentFormat format) {
        if (format != DocumentFormat.XLS && format != DocumentFormat.XLSX) {
            throw new IllegalArgumentException("Excel 解析器只接受 XLS 或 XLSX：" + format);
        }
        this.format = format;
    }

    /** 返回该解析器对应的 Excel 格式。 */
    @Override
    public DocumentFormat format() {
        return format;
    }

    /** 解析工作簿的非空行，并以 Sheet 名作为章节定位。 */
    @Override
    public ParsedDocument parse(Path file, String documentId, String version) throws IOException {
        DocumentSource source = DocumentParsingSupport.source(file, documentId, version, format.name());
        List<DocumentElement> elements = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        int sequence = 0;
        int tableIndex = 0;
        try (InputStream input = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(input)) {
            for (Sheet sheet : workbook) {
                String sectionPath = "Sheet: " + sheet.getSheetName();
                for (Row row : sheet) {
                    String text = rowText(row, formatter);
                    if (text.isEmpty()) {
                        continue;
                    }
                    int rowIndex = row.getRowNum();
                    elements.add(DocumentElement.tableRow(sequence++, sectionPath, text, text,
                            tableIndex, rowIndex));
                }
                tableIndex++;
            }
        } catch (org.apache.poi.EncryptedDocumentException exception) {
            throw new IOException(format.name() + " 文件已加密，无法解析", exception);
        }
        return new ParsedDocument(source, elements);
    }

    /** 将一行单元格格式化为可检索且可定位的文本。 */
    private String rowText(Row row, DataFormatter formatter) {
        List<String> cells = new ArrayList<>();
        for (int column = row.getFirstCellNum(); column >= 0 && column <= row.getLastCellNum(); column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            cells.add(cell == null ? "" : DocumentParsingSupport.cleanText(formatter.formatCellValue(cell)));
        }
        while (!cells.isEmpty() && cells.getLast().isEmpty()) {
            cells.removeLast();
        }
        return DocumentParsingSupport.cleanText(String.join(" | ", cells));
    }
}
