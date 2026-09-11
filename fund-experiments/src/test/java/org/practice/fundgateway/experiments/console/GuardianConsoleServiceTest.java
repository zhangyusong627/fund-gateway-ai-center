package org.practice.fundgateway.experiments.console;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.experiments.console.ConsoleModels.GuardianSimulationRequest;

/** 验证控制台守护回放的正常分支和万条消息降频分支。 */
class GuardianConsoleServiceTest {

    private final GuardianConsoleService service = new GuardianConsoleService();

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
    void shouldReduceTenThousandRiskMessagesToTwoTasks() throws Exception {
        var result = service.simulate(new GuardianSimulationRequest("COMBINED", 10000, false));

        assertThat(result.aggregateWindows()).isEqualTo(10);
        assertThat(result.riskWindows()).isEqualTo(10);
        assertThat(result.diagnosticTasks()).isEqualTo(2);
        assertThat(result.suppressedTasks()).isEqualTo(8);
        assertThat(result.deterministicFindings()).allMatch(finding -> finding.matched());
    }
}
