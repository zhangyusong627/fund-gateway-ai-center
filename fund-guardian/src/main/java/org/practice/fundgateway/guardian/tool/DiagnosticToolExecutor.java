package org.practice.fundgateway.guardian.tool;

import org.springframework.ai.tool.ToolCallback;

/** 为一次 Agent 运行提供有界、只读、白名单内的工具执行上下文。 */
public class DiagnosticToolExecutor {

    private final SyntheticDiagnosticToolRegistry registry;
    private final int maxCalls;

    /** 创建固定工具预算的执行器。 */
    public DiagnosticToolExecutor(SyntheticDiagnosticToolRegistry registry, int maxCalls) {
        if (registry == null || maxCalls < 1) {
            throw new IllegalArgumentException("诊断工具执行器配置无效");
        }
        this.registry = registry;
        this.maxCalls = maxCalls;
    }

    /** 为一次模型工具循环创建独立计数上下文，避免并发运行共享预算。 */
    public Invocation startInvocation() {
        return new Invocation();
    }

    /** 表示一次有独立工具调用预算的只读执行上下文。 */
    public final class Invocation {
        private int calls;

        /** 按白名单执行一个工具，未知工具、空参数和超预算都直接停止。 */
        public String execute(String toolName, String toolInput) {
            if (toolInput == null || toolInput.isBlank()) {
                throw new IllegalArgumentException("诊断工具参数不能为空");
            }
            if (calls >= maxCalls) {
                throw new IllegalStateException("诊断工具调用超过本次预算");
            }
            ToolCallback tool = registry.require(toolName);
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
