package org.practice.fundgateway.knowledge.document;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/** 保存索引任务状态的出站端口。 */
public interface IndexTaskRepository {

    /** 保存新任务或状态更新。 */
    IndexTask save(IndexTask task);

    /** 幂等创建文档版本唯一的索引任务。 */
    IndexTask create(IndexTask task);

    /** 原子抢占可执行任务，抢占失败表示任务已被其他请求处理。 */
    Optional<IndexTask> claim(UUID taskId);

    /** 按任务标识读取任务。 */
    Optional<IndexTask> find(UUID taskId);

    /** 查询全部任务，供控制台列表使用。 */
    List<IndexTask> findAll();
}
