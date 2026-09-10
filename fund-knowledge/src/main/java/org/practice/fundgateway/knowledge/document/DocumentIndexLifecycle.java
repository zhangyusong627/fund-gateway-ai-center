package org.practice.fundgateway.knowledge.document;

/** 约束文档从登记到索引的合法状态流转。 */
public class DocumentIndexLifecycle {

    private DocumentIndexStatus status = DocumentIndexStatus.REGISTERED;

    /** 返回当前文档状态。 */
    public DocumentIndexStatus status() {
        return status;
    }

    /** 标记解析成功。 */
    public void markParsed() {
        transition(DocumentIndexStatus.PARSED, DocumentIndexStatus.REGISTERED);
    }

    /** 标记解析失败。 */
    public void markParseFailed() {
        transition(DocumentIndexStatus.PARSE_FAILED, DocumentIndexStatus.REGISTERED);
    }

    /** 标记索引成功。 */
    public void markIndexed() {
        transition(DocumentIndexStatus.INDEXED, DocumentIndexStatus.PARSED);
    }

    /** 标记索引失败。 */
    public void markIndexFailed() {
        transition(DocumentIndexStatus.INDEX_FAILED, DocumentIndexStatus.PARSED);
    }

    private void transition(DocumentIndexStatus target, DocumentIndexStatus required) {
        if (status != required) {
            throw new IllegalStateException("文档状态不能从 " + status + " 流转到 " + target);
        }
        status = target;
    }
}
