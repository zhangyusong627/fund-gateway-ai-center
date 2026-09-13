package org.practice.fundgateway.common.permission;

/** 接收权限判断审计事件的最小出站端口。 */
@FunctionalInterface
public interface PermissionAuditRecorder {

    /** 记录一条权限判断事件；实现方决定存储位置。 */
    void record(PermissionAuditEvent event);

    /** 返回不产生副作用的审计实现，供旧实验和单元测试显式使用。 */
    static PermissionAuditRecorder noop() {
        return event -> {
        };
    }
}
