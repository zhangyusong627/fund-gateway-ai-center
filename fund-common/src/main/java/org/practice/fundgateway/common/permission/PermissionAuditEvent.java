package org.practice.fundgateway.common.permission;

import java.time.Instant;

/** 保存一次权限判断结果，供拒绝越权和正常授权行为复盘。 */
public record PermissionAuditEvent(
        String traceId,
        String subject,
        String action,
        String resourceType,
        String resourceId,
        Decision decision,
        String reason,
        Instant occurredAt) {

    /** 权限判断的结果。 */
    public enum Decision {
        ALLOWED,
        DENIED
    }

    /** 校验权限审计事件的最小字段。 */
    public PermissionAuditEvent {
        if (isBlank(traceId) || isBlank(subject) || isBlank(action) || isBlank(resourceType)
                || isBlank(resourceId) || decision == null || isBlank(reason) || occurredAt == null) {
            throw new IllegalArgumentException("权限审计事件字段不完整");
        }
    }

    /** 判断字符串是否缺少有效内容。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
