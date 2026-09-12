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

    /** 在内存模式下幂等创建同一文档版本的唯一任务。 */
    @Override
    public synchronized IndexTask create(IndexTask task) {
        return tasks.values().stream()
                .filter(existing -> existing.documentId().equals(task.documentId())
                        && existing.version().equals(task.version()))
                .findFirst()
                .orElseGet(() -> save(task));
    }

    /** 使用 ConcurrentHashMap 的原子计算抢占任务。 */
    @Override
    public Optional<IndexTask> claim(UUID taskId) {
        java.util.concurrent.atomic.AtomicReference<IndexTask> claimed = new java.util.concurrent.atomic.AtomicReference<>();
        tasks.computeIfPresent(taskId, (ignored, current) -> {
            IndexTaskStatus target = current.status() == IndexTaskStatus.CREATED ? IndexTaskStatus.PARSING
                    : current.status() == IndexTaskStatus.PARSED ? IndexTaskStatus.INDEXING : null;
            if (target == null) {
                return current;
            }
            IndexTask next = new IndexTask(current.taskId(), current.documentId(), current.version(),
                    target, null, java.time.Instant.now());
            claimed.set(next);
            return next;
        });
        return Optional.ofNullable(claimed.get());
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
