package org.practice.fundgateway.knowledge.document;

import java.time.Instant;
import java.util.UUID;

/** 表示一次文档解析和索引任务。 */
public record IndexTask(UUID taskId, String documentId, String version,
                        IndexTaskStatus status, String errorMessage, Instant updatedAt) {

    /** 创建新任务。 */
    public static IndexTask created(String documentId, String version) {
        return new IndexTask(UUID.randomUUID(), documentId, version,
                IndexTaskStatus.CREATED, null, Instant.now());
    }
}
