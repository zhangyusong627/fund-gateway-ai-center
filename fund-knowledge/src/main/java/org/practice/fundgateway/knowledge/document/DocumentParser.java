package org.practice.fundgateway.knowledge.document;

import java.io.IOException;
import java.nio.file.Path;

/** 定义所有文档格式共享的解析入口。 */
public interface DocumentParser {

    /** 返回解析器支持的格式。 */
    DocumentFormat format();

    /** 将文件解析为保留来源顺序的统一文档模型。 */
    ParsedDocument parse(Path file, String documentId, String version) throws IOException;
}
