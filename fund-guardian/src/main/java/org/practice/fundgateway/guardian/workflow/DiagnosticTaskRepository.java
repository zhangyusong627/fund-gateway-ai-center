package org.practice.fundgateway.guardian.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 定义诊断工作流所需的任务保存和查询端口。 */
public interface DiagnosticTaskRepository {

    /** 按创建幂等键保存任务，已存在时返回原任务。 */
    DiagnosticTask saveIfAbsent(String creationKey, DiagnosticTask task);

    /** 保存已有诊断聚合的最新状态和追加事实。 */
    void save(DiagnosticTask task);

    /** 按创建幂等键查询已有任务。 */
    Optional<DiagnosticTask> findByCreationKey(String creationKey);

    /** 按任务标识查询聚合。 */
    Optional<DiagnosticTask> findById(UUID taskId);

    /** 查询全部诊断任务。 */
    List<DiagnosticTask> findAll();
}
