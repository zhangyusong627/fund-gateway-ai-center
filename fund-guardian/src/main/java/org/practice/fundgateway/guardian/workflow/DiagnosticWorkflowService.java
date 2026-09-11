package org.practice.fundgateway.guardian.workflow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateDecision;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;

/** 编排诊断创建、人工审批、模拟治理和后台查询用例。 */
public class DiagnosticWorkflowService {

    private final DiagnosticTaskRepository repository;
    private final ModelDiagnosisGate modelGate;
    private final Clock clock;
    private final Duration reviewTtl;

    /** 使用十五分钟审批期限创建诊断工作流服务。 */
    public DiagnosticWorkflowService(DiagnosticTaskRepository repository) {
        this(repository, new ModelDiagnosisGate(), Clock.systemUTC(), Duration.ofMinutes(15));
    }

    /** 注入仓储、门禁、时钟和期限，便于确定性测试。 */
    public DiagnosticWorkflowService(DiagnosticTaskRepository repository, ModelDiagnosisGate modelGate,
                                     Clock clock, Duration reviewTtl) {
        if (repository == null || modelGate == null || clock == null || reviewTtl == null
                || reviewTtl.isZero() || reviewTtl.isNegative()) {
            throw new IllegalArgumentException("诊断工作流服务依赖或审批期限无效");
        }
        this.repository = repository;
        this.modelGate = modelGate;
        this.clock = clock;
        this.reviewTtl = reviewTtl;
    }

    /** 根据固定快照和模型报告创建任务，相同创建键返回同一任务。 */
    public DiagnosticTaskView create(String creationKey, DiagnosisSnapshot snapshot,
                                     ModelDiagnosisReport report) {
        if (creationKey == null || creationKey.isBlank()) {
            throw new IllegalArgumentException("创建幂等键不能为空");
        }
        Optional<DiagnosticTask> existing = repository.findByCreationKey(creationKey);
        if (existing.isPresent()) {
            return existing.orElseThrow().toView();
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("诊断快照不能为空");
        }
        GateDecision decision = modelGate.assess(report, snapshot);
        Instant now = Instant.now(clock);
        DiagnosticTask candidate = new DiagnosticTask(UUID.randomUUID(), creationKey, snapshot.snapshotId(),
                snapshot.riskFingerprint(), report, decision, now, now.plus(reviewTtl));
        return repository.saveIfAbsent(creationKey, candidate).toView();
    }

    /** 批准一个待审批任务。 */
    public DiagnosticTaskView approve(UUID taskId, String operationId, String reviewer, String comment) {
        return review(taskId, operationId, ReviewAction.APPROVE, reviewer, comment);
    }

    /** 拒绝一个待审批任务。 */
    public DiagnosticTaskView reject(UUID taskId, String operationId, String reviewer, String comment) {
        return review(taskId, operationId, ReviewAction.REJECT, reviewer, comment);
    }

    /** 退回一个待审批任务以结束本次诊断。 */
    public DiagnosticTaskView returnForRevision(UUID taskId, String operationId, String reviewer, String comment) {
        return review(taskId, operationId, ReviewAction.RETURN, reviewer, comment);
    }

    /** 对已批准任务执行一次无外部副作用的治理模拟。 */
    public DiagnosticTaskView simulateGovernance(UUID taskId, String operationId, String actionType,
                                                 Map<String, String> parameters, String operator) {
        DiagnosticTask task = requireTask(taskId);
        DiagnosticTaskState before = task.state();
        task.simulate(operationId, actionType, parameters, operator, Instant.now(clock));
        if (!before.equals(task.state())) {
            repository.save(task);
        }
        return task.toView();
    }

    /** 查询一条诊断任务详情及完整时间线。 */
    public Optional<DiagnosticTaskView> findById(UUID taskId) {
        return repository.findById(taskId).map(DiagnosticTask::toView);
    }

    /** 按更新时间倒序查询任务，可按状态过滤。 */
    public List<DiagnosticTaskView> findAll(DiagnosticTaskStatus status) {
        return repository.findAll().stream().map(DiagnosticTask::toView)
                .filter(view -> status == null || view.status() == status)
                .sorted(Comparator.comparing(DiagnosticTaskView::updatedAt).reversed())
                .toList();
    }

    /** 执行指定的人工审批动作。 */
    private DiagnosticTaskView review(UUID taskId, String operationId, ReviewAction action,
                                      String reviewer, String comment) {
        DiagnosticTask task = requireTask(taskId);
        DiagnosticTaskState before = task.state();
        try {
            task.review(operationId, action, reviewer, comment, Instant.now(clock));
        } finally {
            if (!before.equals(task.state())) {
                repository.save(task);
            }
        }
        return task.toView();
    }

    /** 查询必然存在的任务，不存在时转换为明确业务异常。 */
    private DiagnosticTask requireTask(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务标识不能为空");
        }
        return repository.findById(taskId)
                .orElseThrow(() -> new DiagnosticWorkflowException("诊断任务不存在：" + taskId));
    }
}
