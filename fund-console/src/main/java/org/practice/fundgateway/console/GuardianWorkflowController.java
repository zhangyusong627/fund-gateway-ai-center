package org.practice.fundgateway.console;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskStatus;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView;
import org.practice.fundgateway.guardian.workflow.DiagnosticWorkflowService;
import org.practice.fundgateway.guardian.workflow.ReviewAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供诊断任务查询、人工审批和模拟治理接口。 */
@RestController
@RequestMapping("/api/console/guardian/diagnostics")
public class GuardianWorkflowController {

    private final DiagnosticWorkflowService workflowService;
    private final PermissionContext permissionContext;

    /** 注入智能守护诊断工作流。 */
    public GuardianWorkflowController(DiagnosticWorkflowService workflowService) {
        this(workflowService, PermissionContext.syntheticConsole());
    }

    /** 注入当前控制台的显式审批权限上下文。 */
    @org.springframework.beans.factory.annotation.Autowired
    public GuardianWorkflowController(DiagnosticWorkflowService workflowService,
                                      PermissionContext permissionContext) {
        this.workflowService = workflowService;
        this.permissionContext = permissionContext;
    }

    /** 查询全部诊断任务，也可按状态过滤。 */
    @GetMapping
    public List<DiagnosticTaskView> findAll(@RequestParam(required = false) DiagnosticTaskStatus status) {
        return workflowService.findAll(status);
    }

    /** 查询一条任务的报告、审批、模拟结果和完整时间线。 */
    @GetMapping("/{taskId}")
    public DiagnosticTaskView findById(@PathVariable UUID taskId) {
        return workflowService.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("诊断任务不存在：" + taskId));
    }

    /** 执行批准、拒绝或退回操作。 */
    @PostMapping("/{taskId}/reviews")
    public DiagnosticTaskView review(@PathVariable UUID taskId, @RequestBody ReviewRequest request) {
        if (request == null || request.action() == null) {
            throw new IllegalArgumentException("审批动作不能为空");
        }
        return switch (request.action()) {
            case APPROVE -> workflowService.approve(taskId, request.operationId(), request.reviewer(), request.comment(), permissionContext);
            case REJECT -> workflowService.reject(taskId, request.operationId(), request.reviewer(), request.comment(), permissionContext);
            case RETURN -> workflowService.returnForRevision(taskId, request.operationId(), request.reviewer(), request.comment(), permissionContext);
        };
    }

    /** 对人工批准的建议执行无外部副作用的治理模拟。 */
    @PostMapping("/{taskId}/simulations")
    public DiagnosticTaskView simulate(@PathVariable UUID taskId, @RequestBody SimulationRequest request) {
        return workflowService.simulateGovernance(taskId, request.operationId(), request.actionType(),
                request.parameters(), request.operator());
    }

    /** 人工审批请求。 */
    public record ReviewRequest(String operationId, ReviewAction action, String reviewer, String comment) {
    }

    /** 模拟治理请求。 */
    public record SimulationRequest(String operationId, String actionType,
                                    Map<String, String> parameters, String operator) {
    }
}
