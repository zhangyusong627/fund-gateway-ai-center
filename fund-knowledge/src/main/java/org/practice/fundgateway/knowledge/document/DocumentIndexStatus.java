package org.practice.fundgateway.knowledge.document;

/** 表示文档进入知识索引的生命周期状态。 */
public enum DocumentIndexStatus {
    REGISTERED,
    PARSED,
    INDEXED,
    PARSE_FAILED,
    INDEX_FAILED
}
