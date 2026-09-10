package org.practice.fundgateway.knowledge.document;

import java.nio.file.Path;

/** 描述一份需要进入知识库的文档版本。 */
public record DocumentSource(
        String documentId,
        String version,
        Path file,
        String fileSha256) {

    /** 校验文档版本元数据完整。 */
    public DocumentSource {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("文档标识不能为空");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("文档版本不能为空");
        }
        if (file == null) {
            throw new IllegalArgumentException("文档路径不能为空");
        }
        if (fileSha256 == null || fileSha256.isBlank()) {
            throw new IllegalArgumentException("文档哈希不能为空");
        }
    }
}
