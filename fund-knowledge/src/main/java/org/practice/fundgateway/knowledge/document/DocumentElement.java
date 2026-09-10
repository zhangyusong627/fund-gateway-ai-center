package org.practice.fundgateway.knowledge.document;

/** 表示文档中可追溯的段落或表格行。 */
public record DocumentElement(
        ElementType type,
        int sequence,
        String sectionPath,
        String rawText,
        String cleanedText,
        int tableIndex,
        int rowIndex) {

    /** 文档元素类型。 */
    public enum ElementType {
        PARAGRAPH,
        TABLE_ROW
    }

    /** 创建普通段落元素。 */
    public static DocumentElement paragraph(
            int sequence, String sectionPath, String rawText, String cleanedText) {
        return new DocumentElement(ElementType.PARAGRAPH, sequence, sectionPath,
                rawText, cleanedText, -1, -1);
    }

    /** 创建表格行元素。 */
    public static DocumentElement tableRow(
            int sequence, String sectionPath, String rawText, String cleanedText,
            int tableIndex, int rowIndex) {
        return new DocumentElement(ElementType.TABLE_ROW, sequence, sectionPath,
                rawText, cleanedText, tableIndex, rowIndex);
    }
}
