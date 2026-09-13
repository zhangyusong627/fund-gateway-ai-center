package org.practice.fundgateway.console;

import java.util.List;

import org.practice.fundgateway.common.permission.InMemoryPermissionAuditRecorder;
import org.practice.fundgateway.common.permission.PermissionAuditEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供本地演示的权限判断审计查询，不宣称替代生产审计存储。 */
@RestController
@RequestMapping("/api/console/audit/permission-events")
public class PermissionAuditController {

    private final InMemoryPermissionAuditRecorder recorder;

    /** 注入当前进程权限审计记录器。 */
    public PermissionAuditController(InMemoryPermissionAuditRecorder recorder) {
        this.recorder = recorder;
    }

    /** 返回权限允许和拒绝判断的事件快照，最新事件在前。 */
    @GetMapping
    public List<PermissionAuditEvent> findAll() {
        return recorder.snapshot().reversed();
    }
}
