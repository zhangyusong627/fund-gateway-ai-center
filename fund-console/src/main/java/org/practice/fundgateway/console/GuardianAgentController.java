package org.practice.fundgateway.console;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供可复现的会话记忆和真实两轮 Agent 模拟入口。 */
@RestController
@RequestMapping("/api/console/guardian/agent")
public class GuardianAgentController {

    private final GuardianAgentService agentService;

    /** 注入 Agent 应用服务。 */
    public GuardianAgentController(GuardianAgentService agentService) {
        this.agentService = agentService;
    }

    /** 执行一次绑定会话和诊断任务的受控 Agent 模拟。 */
    @PostMapping("/replay")
    public GuardianAgentService.AgentReplayResponse replay(@RequestBody AgentReplayRequest request)
            throws Exception {
        return agentService.replay(request.conversationId(), request.diagnosticTaskId(), request.prompt());
    }

    /** 查询 Agent 是否处于可恢复的中断或等待工具状态。 */
    @GetMapping("/state")
    public org.practice.fundgateway.guardian.agent.AgentExecutionState state(
            @RequestParam String conversationId, @RequestParam UUID diagnosticTaskId) {
        return agentService.executionState(conversationId, diagnosticTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 执行状态不存在"));
    }

    /** 重放未完成的只读 Agent 调查，恢复最近一次用户问题。 */
    @PostMapping("/resume")
    public GuardianAgentService.AgentReplayResponse resume(@RequestParam String conversationId,
                                                            @RequestParam UUID diagnosticTaskId)
            throws Exception {
        return agentService.resume(conversationId, diagnosticTaskId);
    }

    /** Agent 模拟请求。 */
    public record AgentReplayRequest(String conversationId, UUID diagnosticTaskId, String prompt) {
    }
}
