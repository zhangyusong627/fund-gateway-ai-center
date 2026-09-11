package org.practice.fundgateway.knowledge.document;

import java.util.Optional;
import java.util.List;

/** 保存文档登记版本，隔离应用服务与具体存储实现。 */
public interface DocumentVersionRepository {

    /** 按文档和版本读取登记记录。 */
    Optional<DocumentVersionRecord> find(String documentId, String version);

    /** 按文件哈希查找已登记版本。 */
    Optional<DocumentVersionRecord> findBySha256(String sha256);

    /** 查询全部登记版本，供列表页面使用。 */
    List<DocumentVersionRecord> findAll();

    /** 查询一个文档的全部版本。 */
    List<DocumentVersionRecord> findVersions(String documentId);

    /** 保存新的文档版本。 */
    DocumentVersionRecord save(DocumentVersionRecord record);

    /** 更新文档索引状态，不改变版本内容。 */
    DocumentVersionRecord updateStatus(String documentId, String version, DocumentIndexStatus status);
}
