package org.practice.fundgateway.common.permission;

import java.time.Clock;
import java.time.Instant;

/** 在业务边界执行显式权限检查，并把允许和拒绝结果交给审计端口。 */
public class PermissionGuard {

    private final PermissionAuditRecorder auditRecorder;
    private final Clock clock;

    /** 使用系统时钟创建权限检查器。 */
    public PermissionGuard(PermissionAuditRecorder auditRecorder) {
        this(auditRecorder, Clock.systemUTC());
    }

    /** 注入时钟以便稳定验证审计时间。 */
    public PermissionGuard(PermissionAuditRecorder auditRecorder, Clock clock) {
        if (auditRecorder == null || clock == null) {
            throw new IllegalArgumentException("权限检查器依赖不能为空");
        }
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    /** 检查资方范围。 */
    public void requireProvider(PermissionContext context, String provider, String action, String traceId) {
        check(context, context != null && context.allowsProvider(provider), traceId,
                context == null ? "unknown" : context.subject(), action, "PROVIDER", provider,
                "资方不在当前权限范围内");
    }

    /** 检查知识集合、文档和版本范围。 */
    public void requireKnowledge(PermissionContext context, String collectionName, String documentId,
                                 String documentVersion, String action, String traceId) {
        check(context, context != null && context.allowsKnowledge(collectionName, documentId, documentVersion),
                traceId, context == null ? "unknown" : context.subject(), action, "KNOWLEDGE",
                resourceId(collectionName, documentId, documentVersion), "知识资源不在当前权限范围内");
    }

    /** 检查工具白名单以及工具所访问的资方范围。 */
    public void requireTool(PermissionContext context, String toolName, String provider,
                            String action, String traceId) {
        boolean allowed = context != null && context.allowsTool(toolName)
                && (provider == null || context.allowsProvider(provider));
        check(context, allowed, traceId, context == null ? "unknown" : context.subject(), action,
                "TOOL", toolName, provider == null ? "工具不在白名单内" : "工具或资方不在当前权限范围内");
    }

    /** 检查人工审批能力。 */
    public void requireApproval(PermissionContext context, String action, String taskId, String traceId) {
        check(context, context != null && context.approvalAllowed(), traceId,
                context == null ? "unknown" : context.subject(), action, "DIAGNOSTIC_TASK", taskId,
                "当前主体没有人工审批权限");
    }

    /** 记录判断结果，拒绝时抛出统一权限异常。 */
    private void check(PermissionContext context, boolean allowed, String traceId, String subject,
                       String action, String resourceType, String resourceId, String denyReason) {
        String safeTraceId = blankAsUnknown(traceId);
        String safeSubject = blankAsUnknown(subject);
        String safeAction = blankAsUnknown(action);
        String safeResourceType = blankAsUnknown(resourceType);
        String safeResourceId = blankAsUnknown(resourceId);
        PermissionAuditEvent.Decision decision = allowed
                ? PermissionAuditEvent.Decision.ALLOWED : PermissionAuditEvent.Decision.DENIED;
        String reason = allowed ? "权限检查通过" : denyReason;
        auditRecorder.record(new PermissionAuditEvent(safeTraceId, safeSubject, safeAction,
                safeResourceType, safeResourceId, decision, reason, Instant.now(clock)));
        if (!allowed) {
            throw new PermissionDeniedException("权限拒绝：" + denyReason + "，资源=" + safeResourceId);
        }
    }

    /** 组装知识资源审计标识。 */
    private String resourceId(String collectionName, String documentId, String documentVersion) {
        return blankAsUnknown(collectionName) + ":" + blankAsUnknown(documentId)
                + "@" + blankAsUnknown(documentVersion);
    }

    /** 为审计字段提供稳定的缺省值。 */
    private String blankAsUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
