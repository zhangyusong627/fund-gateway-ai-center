package org.practice.fundgateway.knowledge.document;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/** 保存索引任务状态的出站端口。 */
public interface IndexTaskRepository {

    /** 保存新任务或状态更新。 */
    IndexTask save(IndexTask task);

    /** 按任务标识读取任务。 */
    Optional<IndexTask> find(UUID taskId);

    /** 查询全部任务，供控制台列表使用。 */
    List<IndexTask> findAll();
}
