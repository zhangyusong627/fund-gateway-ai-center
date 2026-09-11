package org.practice.fundgateway.knowledge.document;

/** 文档索引任务的确定性状态。 */
public enum IndexTaskStatus {
    CREATED,
    PARSING,
    PARSED,
    INDEXING,
    INDEXED,
    FAILED
}
