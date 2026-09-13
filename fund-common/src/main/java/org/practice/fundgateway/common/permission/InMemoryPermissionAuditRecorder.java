package org.practice.fundgateway.common.permission;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 保存当前进程权限审计事件的轻量记录器，适合本地演示和测试。 */
public class InMemoryPermissionAuditRecorder implements PermissionAuditRecorder {

    private final CopyOnWriteArrayList<PermissionAuditEvent> events = new CopyOnWriteArrayList<>();

    /** 追加权限审计事件。 */
    @Override
    public void record(PermissionAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("权限审计事件不能为空");
        }
        events.add(event);
    }

    /** 返回不可修改的事件快照。 */
    public List<PermissionAuditEvent> snapshot() {
        return List.copyOf(events);
    }
}
