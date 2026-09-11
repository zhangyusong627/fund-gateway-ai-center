package org.practice.fundgateway.guardian.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 使用进程内存保存诊断任务，供第一阶段闭环和单元测试使用。 */
public class InMemoryDiagnosticTaskRepository implements DiagnosticTaskRepository {

    private final Map<UUID, DiagnosticTask> tasks = new LinkedHashMap<>();
    private final Map<String, UUID> creationKeys = new LinkedHashMap<>();

    /** 原子地保存新任务或返回相同创建键对应的已有任务。 */
    @Override
    public synchronized DiagnosticTask saveIfAbsent(String creationKey, DiagnosticTask task) {
        UUID existingId = creationKeys.get(creationKey);
        if (existingId != null) {
            return tasks.get(existingId);
        }
        tasks.put(task.taskId(), task);
        creationKeys.put(creationKey, task.taskId());
        return task;
    }

    /** 保存已有聚合，内存实现用当前对象替换同标识对象。 */
    @Override
    public synchronized void save(DiagnosticTask task) {
        if (!tasks.containsKey(task.taskId())) {
            throw new DiagnosticWorkflowException("不能保存不存在的诊断任务：" + task.taskId());
        }
        tasks.put(task.taskId(), task);
    }

    /** 按创建幂等键查询已有任务。 */
    @Override
    public synchronized Optional<DiagnosticTask> findByCreationKey(String creationKey) {
        UUID taskId = creationKeys.get(creationKey);
        return taskId == null ? Optional.empty() : Optional.ofNullable(tasks.get(taskId));
    }

    /** 按任务标识查询聚合。 */
    @Override
    public synchronized Optional<DiagnosticTask> findById(UUID taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /** 按创建顺序返回任务快照集合。 */
    @Override
    public synchronized List<DiagnosticTask> findAll() {
        return new ArrayList<>(tasks.values());
    }
}
