package org.practice.fundgateway.knowledge.document;

import java.util.Locale;

/** 表示当前版本支持的文档格式。 */
public enum DocumentFormat {
    DOC("doc"),
    DOCX("docx"),
    PDF("pdf"),
    XLS("xls"),
    XLSX("xlsx");

    private final String extension;

    DocumentFormat(String extension) {
        this.extension = extension;
    }

    /** 根据文件扩展名识别格式，不接受无扩展名文件。 */
    public static DocumentFormat from(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("文档必须包含支持的文件扩展名：" + fileName);
        }
        String extension = name.substring(dot + 1);
        for (DocumentFormat format : values()) {
            if (format.extension.equals(extension)) {
                return format;
            }
        }
        throw new IllegalArgumentException("暂不支持的文档格式：" + extension);
    }
}
