package org.practice.fundgateway.knowledge.document;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 使用内存保存文档版本，供第一阶段闭环和单元测试使用。 */
public class InMemoryDocumentVersionRepository implements DocumentVersionRepository {

    private final Map<String, DocumentVersionRecord> records = new ConcurrentHashMap<>();

    /** 按文档标识和版本读取记录。 */
    @Override
    public Optional<DocumentVersionRecord> find(String documentId, String version) {
        return Optional.ofNullable(records.get(key(documentId, version)));
    }

    /** 按哈希查找记录，识别内容重复上传。 */
    @Override
    public Optional<DocumentVersionRecord> findBySha256(String sha256) {
        return records.values().stream().filter(record -> record.source().fileSha256().equals(sha256)).findFirst();
    }

    /** 返回全部登记版本的快照。 */
    @Override
    public List<DocumentVersionRecord> findAll() {
        return List.copyOf(records.values());
    }

    /** 返回指定文档的全部版本。 */
    @Override
    public List<DocumentVersionRecord> findVersions(String documentId) {
        return records.values().stream().filter(record -> record.source().documentId().equals(documentId)).toList();
    }

    /** 保存记录；同一文档版本不可覆盖。 */
    @Override
    public DocumentVersionRecord save(DocumentVersionRecord record) {
        String recordKey = key(record.source().documentId(), record.source().version());
        DocumentVersionRecord previous = records.putIfAbsent(recordKey, record);
        if (previous != null) {
            throw new IllegalStateException("文档版本已存在，不允许覆盖：" + recordKey);
        }
        return record;
    }

    /** 更新内存中的文档状态。 */
    @Override
    public DocumentVersionRecord updateStatus(String documentId, String version, DocumentIndexStatus status) {
        String recordKey = key(documentId, version);
        DocumentVersionRecord current = records.get(recordKey);
        if (current == null) {
            throw new IllegalArgumentException("文档版本不存在：" + recordKey);
        }
        DocumentVersionRecord updated = new DocumentVersionRecord(current.source(), status,
                current.elements(), current.chunks());
        records.put(recordKey, updated);
        return updated;
    }

    /** 生成稳定的内存索引键。 */
    private String key(String documentId, String version) {
        return documentId + "@" + version;
    }
}
