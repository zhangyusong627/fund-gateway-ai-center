package org.practice.fundgateway.guardian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.guardian.diagnosis.DiagnosisSnapshot;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.ModelFinding;
import org.practice.fundgateway.guardian.diagnosis.RuleFinding;
import org.practice.fundgateway.guardian.workflow.DiagnosticTask;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskStatus;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView;
import org.practice.fundgateway.guardian.workflow.DiagnosticWorkflowService;
import org.practice.fundgateway.guardian.workflow.InMemoryDiagnosticTaskRepository;
import org.practice.fundgateway.guardian.workflow.ReviewAction;

/** 固定验收证据不足、模型失败、审批和任务恢复等受控 Agent 分支。 */
class GuardianFailureAcceptanceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-20T00:00:00Z");

    /** 模型报告非法时必须停止并且不得创建诊断任务。 */
    @Test
    void modelFailureMustStopBeforeTaskCreation() {
        InMemoryDiagnosticTaskRepository repository = new InMemoryDiagnosticTaskRepository();
        DiagnosticWorkflowService service = new DiagnosticWorkflowService(repository);

        assertThrows(IllegalArgumentException.class,
                () -> service.create("model-failure-1", snapshot(true), invalidReport()));

        assertTrue(repository.findAll().isEmpty());
    }

    /** 人工拒绝必须成为终态，并保留审批原因。 */
    @Test
    void approvalRejectionMustBeTerminalAndTraceable() {
        DiagnosticWorkflowService service = service(new InMemoryDiagnosticTaskRepository());
        DiagnosticTaskView pending = service.create("approval-reject-1", snapshot(true), report(false));

        DiagnosticTaskView rejected = service.reject(pending.taskId(), "review-reject-1", "owner", "证据不足");

        assertEquals(DiagnosticTaskStatus.REJECTED, rejected.status());
        assertEquals(ReviewAction.REJECT, rejected.review().action());
        assertEquals("证据不足", rejected.review().comment());
    }

    /** 重复创建和重复操作必须保持幂等，不能追加第二条事实。 */
    @Test
    void duplicateRequestMustNotCreateOrApproveTwice() {
        DiagnosticWorkflowService service = service(new InMemoryDiagnosticTaskRepository());
        DiagnosticTaskView first = service.create("duplicate-1", snapshot(true), report(false));
        DiagnosticTaskView duplicate = service.create("duplicate-1", null, null);
        DiagnosticTaskView approved = service.approve(first.taskId(), "review-duplicate-1", "owner", "允许模拟");
        DiagnosticTaskView repeated = service.approve(first.taskId(), "review-duplicate-1", "owner", "重复请求");

        assertEquals(first.taskId(), duplicate.taskId());
        assertEquals(approved.timeline().size(), repeated.timeline().size());
        assertEquals(1, service.findAll(null).size());
    }

    /** 持久状态恢复后必须保留审批、模拟治理和操作幂等事实。 */
    @Test
    void restoredTaskMustContinueWithOriginalFacts() {
        InMemoryDiagnosticTaskRepository repository = new InMemoryDiagnosticTaskRepository();
        DiagnosticWorkflowService service = service(repository);
        DiagnosticTaskView pending = service.create("restore-1", snapshot(true), report(false));
        service.approve(pending.taskId(), "review-restore-1", "owner", "允许模拟");
        DiagnosticTaskView simulated = service.simulateGovernance(pending.taskId(), "simulation-restore-1",
                "ADJUST_RATE_LIMIT", Map.of("qps", "80"), "owner");

        DiagnosticTask restored = repository.findById(pending.taskId()).orElseThrow();
        DiagnosticTask recovered = DiagnosticTask.restore(restored.state());
        recovered.simulate("simulation-restore-1", "ADJUST_RATE_LIMIT", Map.of("qps", "80"), "owner", BASE_TIME);

        assertEquals(DiagnosticTaskStatus.SIMULATED, recovered.toView().status());
        assertEquals(simulated.timeline().size(), recovered.toView().timeline().size());
        assertEquals(1, recovered.toView().simulations().size());
        assertEquals("允许模拟", recovered.toView().review().comment());
    }

    /** 创建使用固定 UTC 时钟的工作流服务。 */
    private DiagnosticWorkflowService service(InMemoryDiagnosticTaskRepository repository) {
        return new DiagnosticWorkflowService(repository, new ModelDiagnosisGate(),
                Clock.fixed(BASE_TIME, ZoneOffset.UTC), Duration.ofMinutes(15));
    }

    /** 构造含确定性规则证据的固定快照。 */
    private DiagnosisSnapshot snapshot(boolean matched) {
        return new DiagnosisSnapshot("snapshot-fixed", BASE_TIME, null,
                List.of(new RuleFinding("R001", matched, "HIGH", "QPS 超过阈值", "执行人工核查", true)),
                null, List.of(), "fingerprint-fixed");
    }

    /** 构造可通过结构门禁但与规则冲突的模型报告。 */
    private ModelDiagnosisReport report(boolean matched) {
        return new ModelDiagnosisReport("发现接口调用风险", "HIGH",
                List.of(new ModelFinding("R001", matched, "指标显示调用量异常", "建议人工核查", false)), false);
    }

    /** 构造应被模型结构门禁拒绝的报告。 */
    private ModelDiagnosisReport invalidReport() {
        return new ModelDiagnosisReport("", "UNKNOWN", List.of(), false);
    }
}
