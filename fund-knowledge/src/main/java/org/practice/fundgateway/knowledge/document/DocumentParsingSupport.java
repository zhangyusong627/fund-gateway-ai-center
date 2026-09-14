package org.practice.fundgateway.knowledge.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 提供不同格式解析器共用的文件校验和及文本清洗能力。 */
final class DocumentParsingSupport {

    private DocumentParsingSupport() {
    }

    /** 校验文件存在并创建统一来源对象。 */
    static DocumentSource source(Path file, String documentId, String version, String format)
            throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException(format + " 文件不存在：" + file);
        }
        return new DocumentSource(documentId, version, file, sha256(file));
    }

    /** 将连续空白折叠，保留业务字段、数值、单位和条件表达。 */
    static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a0', ' ')
                .replaceAll("[\\t\\r\\n]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    /** 计算文件 SHA-256，避免把完整文件加载到内存。 */
    static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.security.DigestInputStream digestInput =
                         new java.security.DigestInputStream(input, digest)) {
                byte[] buffer = new byte[8192];
                while (digestInput.read(buffer) != -1) {
                    // 读取并更新摘要，内容不在内存中累积。
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 未提供 SHA-256", exception);
        }
    }
}
