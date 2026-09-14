package org.practice.fundgateway.knowledge.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 按文件格式选择解析器，集中管理解析器注册和错误提示。 */
public class DocumentParserRegistry {

    private final Map<DocumentFormat, DocumentParser> parsers;

    /** 使用传入解析器构建不可变注册表。 */
    public DocumentParserRegistry(List<? extends DocumentParser> parsers) {
        if (parsers == null || parsers.isEmpty()) {
            throw new IllegalArgumentException("至少需要注册一个文档解析器");
        }
        EnumMap<DocumentFormat, DocumentParser> registered = new EnumMap<>(DocumentFormat.class);
        for (DocumentParser parser : parsers) {
            if (parser == null) {
                throw new IllegalArgumentException("文档解析器不能为空");
            }
            if (registered.put(parser.format(), parser) != null) {
                throw new IllegalArgumentException("文档格式重复注册：" + parser.format());
            }
        }
        this.parsers = Map.copyOf(registered);
    }

    /** 使用当前已实现的 Office 解析器创建默认注册表。 */
    public static DocumentParserRegistry defaultRegistry() {
        return new DocumentParserRegistry(List.of(
                new DocDocumentParser(), new DocxDocumentParser(),
                new ExcelDocumentParser(DocumentFormat.XLS),
                new ExcelDocumentParser(DocumentFormat.XLSX), new PdfDocumentParser()));
    }

    /** 根据文件扩展名解析文档。 */
    public ParsedDocument parse(Path file, String documentId, String version) throws IOException {
        if (file == null) {
            throw new IOException("文档路径不能为空");
        }
        DocumentFormat format = DocumentFormat.from(file.getFileName().toString());
        DocumentParser parser = parsers.get(format);
        if (parser == null) {
            throw new IOException("尚未注册 " + format + " 文档解析器");
        }
        return parser.parse(file, documentId, version);
    }
}
