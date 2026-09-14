# M8 任务卡：记忆管理与受控多轮 Agent

- 对应路线图：M8
- 状态：实现已完成，等待学习者独立验收

## 业务目标

让智能守护能够围绕一个诊断任务恢复会话上下文，使用白名单只读工具进行有限多轮调查，并保存可恢复、可审计的记忆。

## 输入 / 输出

- 输入：`conversationId`、`diagnosticTaskId`、当前用户问题、诊断快照和权限上下文。
- 输出：结构化诊断报告、Agent 轮次轨迹、短期记忆、摘要和可选的已确认案例记忆。

## 状态与异常分支

`STARTED → CONTEXT_LOADED → MODEL_DECIDING → TOOL_REQUESTED → TOOL_EXECUTED → RESULT_REINJECTED → FINAL_ANSWER`。

异常进入 `TOOL_DENIED`、`BUDGET_EXCEEDED`、`TIMEOUT`、`EVIDENCE_INSUFFICIENT`、`HUMAN_REVIEW` 或 `FAILED`。

## 验收方式

- Prompt 版本可追踪；会话和诊断任务不串线。
- 工具结果能够回灌，达到终止条件后停止。
- 超时、拒绝、超预算、摘要失败和重启恢复均有测试。
- 仅人工审批通过的诊断可以形成长期案例记忆。
- `mvn -B verify` 通过，并保存 M8 学习验收记录。
