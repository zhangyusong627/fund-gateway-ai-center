package org.practice.fundgateway.common.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 验证资方、知识、工具和审批四类最小权限边界及拒绝审计。 */
class PermissionGuardTest {

    private final InMemoryPermissionAuditRecorder recorder = new InMemoryPermissionAuditRecorder();
    private final PermissionGuard guard = new PermissionGuard(recorder,
            Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC));

    /** 资方不在白名单时必须拒绝并记录 DENIED。 */
    @Test
    void shouldDenyProviderOutsideScopeAndAudit() {
        PermissionContext context = context(Set.of("shengheng"), Set.of("querySyntheticContract"), false);

        assertThrows(PermissionDeniedException.class,
                () -> guard.requireProvider(context, "synthetic-provider", "DIAGNOSTIC_READ", "trace-provider"));

        PermissionAuditEvent event = recorder.snapshot().getFirst();
        assertEquals(PermissionAuditEvent.Decision.DENIED, event.decision());
        assertEquals("trace-provider", event.traceId());
        assertEquals("PROVIDER", event.resourceType());
    }

    /** 知识范围必须同时匹配集合、文档和版本，不能只匹配集合。 */
    @Test
    void shouldRequireExactKnowledgeScope() {
        PermissionContext context = context(Set.of("synthetic-provider"), Set.of("querySyntheticContract"), false);
        PermissionContext.KnowledgeScope scope = new PermissionContext.KnowledgeScope(
                "fund-gateway-contracts", "shengheng-contract", "v1");
        PermissionContext scopedContext = new PermissionContext(context.subject(), context.providerScope(), Set.of(scope),
                context.allowedTools(), context.approvalAllowed());

        guard.requireKnowledge(scopedContext, "fund-gateway-contracts", "shengheng-contract", "v1",
                "KNOWLEDGE_QUERY", "trace-knowledge-ok");
        assertThrows(PermissionDeniedException.class, () -> guard.requireKnowledge(scopedContext,
                "fund-gateway-contracts", "shengheng-contract", "v2", "KNOWLEDGE_QUERY", "trace-knowledge-deny"));
        assertEquals(PermissionAuditEvent.Decision.DENIED, recorder.snapshot().getLast().decision());
    }

    /** 审批权限关闭时，任务状态变更入口必须拒绝。 */
    @Test
    void shouldDenyReviewWithoutApprovalPermission() {
        PermissionContext context = context(Set.of("synthetic-provider"), Set.of("querySyntheticContract"), false);

        assertThrows(PermissionDeniedException.class,
                () -> guard.requireApproval(context, "DIAGNOSTIC_REVIEW_APPROVE", "task-1", "trace-review"));

        assertEquals("DIAGNOSTIC_REVIEW_APPROVE", recorder.snapshot().getFirst().action());
    }

    /** 构造只拥有一项工具和一项资方范围的受限上下文。 */
    private PermissionContext context(Set<String> providers, Set<String> tools, boolean approvalAllowed) {
        return new PermissionContext("test-subject", providers,
                Set.of(new PermissionContext.KnowledgeScope("*", "*", "*")), tools, approvalAllowed);
    }
}
