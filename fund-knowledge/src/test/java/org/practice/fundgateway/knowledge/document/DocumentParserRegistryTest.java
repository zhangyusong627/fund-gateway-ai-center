package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证格式识别、解析器注册和未注册格式的失败边界。 */
class DocumentParserRegistryTest {

    /** 验证扩展名识别不受大小写影响。 */
    @Test
    void shouldRecognizeSupportedExtensionsCaseInsensitively() {
        assertEquals(DocumentFormat.DOC, DocumentFormat.from("sample.DOC"));
        assertEquals(DocumentFormat.DOCX, DocumentFormat.from("sample.docx"));
        assertEquals(DocumentFormat.PDF, DocumentFormat.from("sample.Pdf"));
        assertEquals(DocumentFormat.XLS, DocumentFormat.from("sample.xls"));
        assertEquals(DocumentFormat.XLSX, DocumentFormat.from("sample.XLSX"));
    }

    /** 验证只有注册的解析器才能被调用。 */
    @Test
    void shouldRejectKnownFormatWithoutRegisteredParser() throws Exception {
        Path file = Files.createTempFile("parser-registry-", ".pdf");
        try {
            DocumentParserRegistry registry = new DocumentParserRegistry(List.of(new DocxDocumentParser()));
            assertThrows(IOException.class, () -> registry.parse(file, "doc", "v1"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** 验证注册表拒绝同一格式的重复解析器。 */
    @Test
    void shouldRejectDuplicateFormat() {
        DocumentParser parser = new DocumentParser() {
            @Override
            public DocumentFormat format() {
                return DocumentFormat.DOCX;
            }

            @Override
            public ParsedDocument parse(Path file, String documentId, String version) {
                return null;
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentParserRegistry(List.of(new DocxDocumentParser(), parser)));
    }
}
