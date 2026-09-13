package org.practice.fundgateway.guardian.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.common.permission.InMemoryPermissionAuditRecorder;
import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.common.permission.PermissionDeniedException;
import org.practice.fundgateway.common.permission.PermissionGuard;

/** 验证工具执行器在真正回调前同时执行工具白名单和资方范围检查。 */
class DiagnosticToolExecutorPermissionTest {

    /** 不在工具白名单的模型请求不能消耗预算或调用工具。 */
    @Test
    void shouldRejectToolOutsideAllowlistBeforeCallback() {
        InMemoryPermissionAuditRecorder recorder = new InMemoryPermissionAuditRecorder();
        DiagnosticToolExecutor executor = new DiagnosticToolExecutor(new SyntheticDiagnosticToolRegistry(), 1,
                new PermissionGuard(recorder));
        PermissionContext context = new PermissionContext("restricted-agent", Set.of("synthetic-provider"),
                Set.of(new PermissionContext.KnowledgeScope("*", "*", "*")),
                Set.of("querySyntheticMetrics"), false);

        DiagnosticToolExecutor.Invocation invocation = executor.startInvocation(context, "trace-tool");
        assertThrows(PermissionDeniedException.class, () -> invocation.execute("querySyntheticContract",
                "{\"provider\":\"synthetic-provider\",\"interface\":\"credit-apply\"}"));

        assertEquals(0, invocation.calls());
        assertEquals("TOOL", recorder.snapshot().getFirst().resourceType());
    }

    /** 工具虽在白名单但资方范围不匹配时仍不能进入回调。 */
    @Test
    void shouldRejectToolProviderOutsideScopeBeforeCallback() {
        InMemoryPermissionAuditRecorder recorder = new InMemoryPermissionAuditRecorder();
        DiagnosticToolExecutor executor = new DiagnosticToolExecutor(new SyntheticDiagnosticToolRegistry(), 1,
                new PermissionGuard(recorder));
        PermissionContext context = new PermissionContext("restricted-agent", Set.of("other-provider"),
                Set.of(new PermissionContext.KnowledgeScope("*", "*", "*")),
                Set.of("querySyntheticMetrics"), false);

        DiagnosticToolExecutor.Invocation invocation = executor.startInvocation(context, "trace-provider-tool");
        assertThrows(PermissionDeniedException.class, () -> invocation.execute("querySyntheticMetrics",
                "{\"provider\":\"synthetic-provider\",\"interface\":\"credit-apply\"}"));

        assertEquals(0, invocation.calls());
        assertEquals(org.practice.fundgateway.common.permission.PermissionAuditEvent.Decision.DENIED,
                recorder.snapshot().getLast().decision());
    }
}
