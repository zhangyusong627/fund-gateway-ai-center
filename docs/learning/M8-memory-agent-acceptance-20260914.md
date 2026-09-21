# M8 记忆管理与受控 Agent 闭环验收

## 已完成

- Prompt 已从诊断业务类中移出，按 classpath 资源和名称/版本读取。
- 会话消息按 `conversationId + diagnosticTaskId` 隔离，支持过期过滤、最近窗口和摘要。
- PostgreSQL 已增加会话消息、会话摘要和已确认案例记忆表定义。
- 受控 Agent 已实现有限轮次、只读工具白名单、权限上下文、工具结果回灌和预算终止。
- 正式控制台新增 `/api/console/guardian/agent/replay`，真实配置下执行两轮 DeepSeek 工具调用并保存会话消息。
- 两轮请求均通过上下文渲染器注入历史摘要、最近消息和工具轨迹；超过字符预算时生成版本化摘要。
- 长期案例要求证据引用和人工审批标识，并支持有效案例匹配召回。
- 新诊断任务已将完整 `DiagnosisSnapshot` 写入 `snapshot_json`，重启后可恢复；旧的 `snapshot_json` 为空记录仍可查询。
- 真实 HTTP 模拟已完成：DeepSeek 首轮选择只读工具、Java 执行并回灌、第二轮返回最终回答，执行状态为 `COMPLETED / turn=2 / toolCalls=1`。
- 已完成 `PENDING_APPROVAL -> APPROVED -> ACTIVE` 案例链路；重启 Console 后长期案例仍可召回，`evidenceRefs` 恢复为 `List<String>`。
- 版本化诊断 Prompt 增加严格 JSON 字段示例，模型输出不符合固定结构时仍由门禁转人工审核，不放宽 Java 校验。

## 验证

```text
mvn -B verify
BUILD SUCCESS
fund-guardian: 63 tests, 0 failures; fund-console: 9 tests, 0 failures
git diff --check
通过

HTTP / PostgreSQL 模拟：
- 新建诊断任务：`68b8dc56-792e-4c81-a563-134d031a2b66`，重启后状态仍为 `APPROVED`，数据库 `tasks_with_snapshot=1`。
- 长期案例查询：重启前后均返回 1 条 `ACTIVE` 案例，证据引用为 JSON 数组并恢复为 Java 列表。
- Agent 状态：`COMPLETED`，`turn=2`，`toolCalls=1`；执行游标在 Console 重启后仍可查询。
```

## 当前边界

本轮可以宣称：在当前合成数据和只读治理范围内，Prompt 管理、会话短期记忆、摘要型上下文管理、人工确认后的长期案例记忆，以及受轮次/工具预算约束的两轮 Agent 闭环已完成。`resume` 是安全重放，不是从模型中间 tool-call 精确续跑；尚不包含真实认证、生产副作用、多 Agent、Redis 和自动修复，因此不能表述为生产级 Agent 平台。
