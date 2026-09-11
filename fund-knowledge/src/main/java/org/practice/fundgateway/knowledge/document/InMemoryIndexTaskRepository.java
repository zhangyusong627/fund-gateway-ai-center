package org.practice.fundgateway.knowledge.document;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 使用内存保存索引任务，后续可替换为关系数据库实现。 */
public class InMemoryIndexTaskRepository implements IndexTaskRepository {

    private final Map<UUID, IndexTask> tasks = new ConcurrentHashMap<>();

    /** 保存任务状态。 */
    @Override
    public IndexTask save(IndexTask task) {
        tasks.put(task.taskId(), task);
        return task;
    }

    /** 读取任务状态。 */
    @Override
    public Optional<IndexTask> find(UUID taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /** 返回全部任务的快照。 */
    @Override
    public List<IndexTask> findAll() {
        return List.copyOf(tasks.values());
    }
}
