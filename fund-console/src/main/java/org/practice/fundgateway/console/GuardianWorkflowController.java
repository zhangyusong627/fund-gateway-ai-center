package org.practice.fundgateway.console;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskStatus;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView;
import org.practice.fundgateway.guardian.workflow.DiagnosticWorkflowService;
import org.practice.fundgateway.guardian.workflow.ReviewAction;
import org.practice.fundgateway.guardian.memory.ConfirmedIncidentMemory;
import org.practice.fundgateway.guardian.memory.ConfirmedIncidentMemoryService;
import org.practice.fundgateway.guardian.memory.IncidentMemoryPort;
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
    private final ConfirmedIncidentMemoryService incidentMemoryService;
    private final IncidentMemoryPort incidentMemory;

    /** 注入智能守护诊断工作流。 */
    public GuardianWorkflowController(DiagnosticWorkflowService workflowService) {
        this(workflowService, PermissionContext.syntheticConsole());
    }

    /** 注入当前控制台的显式审批权限上下文。 */
    public GuardianWorkflowController(DiagnosticWorkflowService workflowService,
                                      PermissionContext permissionContext) {
        this(workflowService, permissionContext, null, null);
    }

    /** 注入审批后案例记忆服务。 */
    @org.springframework.beans.factory.annotation.Autowired
    public GuardianWorkflowController(DiagnosticWorkflowService workflowService,
                                      PermissionContext permissionContext,
                                      ConfirmedIncidentMemoryService incidentMemoryService,
                                      IncidentMemoryPort incidentMemory) {
        this.workflowService = workflowService;
        this.permissionContext = permissionContext;
        this.incidentMemoryService = incidentMemoryService;
        this.incidentMemory = incidentMemory;
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

    /** 审批通过后沉淀长期案例记忆。 */
    @PostMapping("/{taskId}/memories")
    public ConfirmedIncidentMemory promoteMemory(@PathVariable UUID taskId, @RequestBody MemoryRequest request) {
        if (incidentMemoryService == null || incidentMemory == null) {
            throw new IllegalStateException("长期案例记忆未装配");
        }
        DiagnosticTaskView task = workflowService.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("诊断任务不存在：" + taskId));
        return incidentMemoryService.promote(task, workflowService.findSnapshot(taskId).orElse(null),
                request.approvalId());
    }

    /** 查询指定资方和接口下按症状匹配的有效案例记忆。 */
    @GetMapping("/memories")
    public List<ConfirmedIncidentMemory> findMemories(@RequestParam String providerId,
                                                      @RequestParam String interfaceId,
                                                      @RequestParam(required = false) String symptom) {
        if (incidentMemory == null) throw new IllegalStateException("长期案例记忆未装配");
        return incidentMemory.findActive(providerId, interfaceId, symptom);
    }

    /** 人工审批请求。 */
    public record ReviewRequest(String operationId, ReviewAction action, String reviewer, String comment) {
    }

    /** 模拟治理请求。 */
    public record SimulationRequest(String operationId, String actionType,
                                    Map<String, String> parameters, String operator) {
    }

    /** 长期案例记忆沉淀请求。 */
    public record MemoryRequest(String approvalId) { }
}
