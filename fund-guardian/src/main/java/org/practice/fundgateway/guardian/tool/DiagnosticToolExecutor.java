package org.practice.fundgateway.guardian.tool;

import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.common.permission.PermissionGuard;
import org.springframework.ai.tool.ToolCallback;

/** 为一次 Agent 运行提供有界、只读、白名单内的工具执行上下文。 */
public class DiagnosticToolExecutor {

    private final SyntheticDiagnosticToolRegistry registry;
    private final int maxCalls;
    private final PermissionGuard permissionGuard;

    /** 创建固定工具预算的执行器。 */
    public DiagnosticToolExecutor(SyntheticDiagnosticToolRegistry registry, int maxCalls) {
        this(registry, maxCalls, new PermissionGuard(
                org.practice.fundgateway.common.permission.PermissionAuditRecorder.noop()));
    }

    /** 注入权限检查器，确保工具白名单判断发生在真正回调前。 */
    public DiagnosticToolExecutor(SyntheticDiagnosticToolRegistry registry, int maxCalls,
                                  PermissionGuard permissionGuard) {
        if (registry == null || maxCalls < 1 || permissionGuard == null) {
            throw new IllegalArgumentException("诊断工具执行器配置无效");
        }
        this.registry = registry;
        this.maxCalls = maxCalls;
        this.permissionGuard = permissionGuard;
    }

    /** 为一次模型工具循环创建独立计数上下文，避免并发运行共享预算。 */
    public Invocation startInvocation() {
        return startInvocation(PermissionContext.syntheticDiagnostic(), "diagnostic-tool-invocation");
    }

    /** 使用一次调用专属的权限上下文和审计关联号启动工具循环。 */
    public Invocation startInvocation(PermissionContext context, String traceId) {
        if (context == null || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("工具调用权限上下文和 traceId 不能为空");
        }
        return new Invocation(context, traceId);
    }

    /** 表示一次有独立工具调用预算的只读执行上下文。 */
    public final class Invocation {
        private final PermissionContext context;
        private final String traceId;
        private int calls;

        /** 保存一次工具循环的权限和审计边界。 */
        private Invocation(PermissionContext context, String traceId) {
            this.context = context;
            this.traceId = traceId;
        }

        /** 按白名单执行一个工具，未知工具、空参数和超预算都直接停止。 */
        public String execute(String toolName, String toolInput) {
            if (toolInput == null || toolInput.isBlank()) {
                throw new IllegalArgumentException("诊断工具参数不能为空");
            }
            if (calls >= maxCalls) {
                throw new IllegalStateException("诊断工具调用超过本次预算");
            }
            permissionGuard.requireTool(context, toolName, null, "DIAGNOSTIC_TOOL_READ", traceId);
            ToolCallback tool = registry.require(toolName);
            SyntheticToolInput.Target target = SyntheticToolInput.parse(toolInput,
                    "只支持合成资方的授信申请查询");
            permissionGuard.requireTool(context, toolName, target.provider(),
                    "DIAGNOSTIC_TOOL_READ", traceId);
            calls++;
            try {
                return tool.call(toolInput);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("只读诊断工具执行失败：" + toolName, exception);
            }
        }

        /** 返回当前调用次数，供实验审计和测试使用。 */
        public int calls() {
            return calls;
        }
    }
}
