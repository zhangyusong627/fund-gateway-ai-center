package org.practice.fundgateway.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.console.ConsoleModels.GuardianSimulationRequest;
import org.practice.fundgateway.common.permission.InMemoryPermissionAuditRecorder;
import org.practice.fundgateway.common.permission.PermissionAuditEvent;
import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.common.permission.PermissionDeniedException;
import org.practice.fundgateway.common.permission.PermissionGuard;
import org.practice.fundgateway.guardian.ai.ModelDiagnosisFacade;
import org.practice.fundgateway.guardian.ai.ModelGateway;
import org.practice.fundgateway.guardian.audit.InMemoryModelCallAuditRepository;
import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.practice.fundgateway.guardian.workflow.DiagnosticWorkflowService;
import org.practice.fundgateway.guardian.workflow.InMemoryDiagnosticTaskRepository;

/** 验证控制台守护回放的正常分支和万条消息降频分支。 */
class GuardianConsoleServiceTest {

    private final GuardianConsoleService service = new GuardianConsoleService(
            new DiagnosticWorkflowService(new InMemoryDiagnosticTaskRepository()));

    /** 正常指标不应产生风险窗口和诊断任务。 */
    @Test
    void shouldNotCreateTaskForNormalMetrics() throws Exception {
        var result = service.simulate(new GuardianSimulationRequest("NORMAL", 1000, false));

        assertThat(result.metricMessages()).isEqualTo(1000);
        assertThat(result.aggregateWindows()).isEqualTo(1);
        assertThat(result.riskWindows()).isZero();
        assertThat(result.diagnosticTasks()).isZero();
        assertThat(result.actualModelCalls()).isZero();
    }

    /** 一万条组合风险消息应被十秒窗口和六十秒冷却显著降频。 */
    @Test
    void shouldReduceTenThousandRiskMessagesToNoTaskWhenModelIsDisabled() throws Exception {
        var result = service.simulate(new GuardianSimulationRequest("COMBINED", 10000, false));

        assertThat(result.aggregateWindows()).isEqualTo(10);
        assertThat(result.riskWindows()).isEqualTo(10);
        assertThat(result.diagnosticTasks()).isZero();
        assertThat(result.suppressedTasks()).isEqualTo(8);
        assertThat(result.deterministicFindings()).allMatch(finding -> finding.matched());
    }

    /** 诊断入口发现资方越权时应在生成指标前拒绝并记录审计。 */
    @Test
    void shouldRejectGuardianProviderOutsideScope() throws Exception {
        InMemoryPermissionAuditRecorder recorder = new InMemoryPermissionAuditRecorder();
        PermissionContext restricted = new PermissionContext("read-only-agent", java.util.Set.of("other-provider"),
                java.util.Set.of(new PermissionContext.KnowledgeScope("*", "*", "*")),
                PermissionContext.SYNTHETIC_DIAGNOSTIC_TOOLS, false);
        ModelDiagnosisFacade facade = new ModelDiagnosisFacade(ModelGateway.unavailable(),
                new ModelAuditApplicationService(new InMemoryModelCallAuditRepository()));
        try {
            GuardianConsoleService guarded = new GuardianConsoleService(
                    new DiagnosticWorkflowService(new InMemoryDiagnosticTaskRepository()), null, null, facade,
                    new PermissionGuard(recorder), restricted);

            assertThrows(PermissionDeniedException.class,
                    () -> guarded.simulate(new GuardianSimulationRequest("COMBINED", 100, false), restricted));
            assertThat(recorder.snapshot()).hasSize(1);
            assertThat(recorder.snapshot().getFirst().decision())
                    .isEqualTo(PermissionAuditEvent.Decision.DENIED);
        } finally {
            facade.close();
        }
    }
}
