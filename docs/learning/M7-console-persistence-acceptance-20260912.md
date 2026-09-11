# M7 管理后台持久化与联合回放验收

## 验证范围

- PostgreSQL 16 + pgvector Schema 初始化。
- 升恒消费金融接口文档全量解析、分片、Embedding 和发布。
- 在线检索、智能守护、DeepSeek、Java 门禁、人工审批、模拟治理和模型成本审计。
- 控制台容器重启后的状态恢复。

## 结果

| 验收项 | 结果 |
|---|---|
| 文档解析 | 通过，1187 个元素、375 个段落、812 个表格行、905 个分片 |
| 离线索引 | 通过，905 个分片写入 `fund-gateway-contracts` 并发布为 `PUBLISHED` |
| 在线检索 | 通过，`applyAmt / BigDecimal / 必填` Top-1 命中，状态 `ACCEPTED` |
| 指标降频 | 通过，10000 条消息回放收敛为 10 个窗口和 2 个诊断任务 |
| 真实模型 | 通过，DeepSeek `deepseek-v4-flash` 调用 1 次，JSON 门禁 `ACCEPTED` |
| 审批与治理 | 通过，`PENDING_APPROVAL → APPROVED → SIMULATED`，结果为 `SIMULATED_SUCCESS` |
| 成本审计 | 通过，原始请求/响应、Token、状态、价格版本和估算成本可查询 |
| 重启恢复 | 通过，重启 `fund-console` 后文档、索引、诊断、审批和模拟记录仍可读取 |
| 构建 | 通过，`mvn -B verify`、`docker compose config -q`、`git diff --check` |

## 限制

- 指标、文档和治理动作均为合成或用户提供材料；没有真实生产接入和真实写操作。
- 页面自动化命令 `agent-browser` 未安装，本轮用 HTTP 端到端接口、静态页面 HTTP 200 和 Node.js 脚本语法检查替代截图验证。
- DeepSeek 价格快照使用本地演示价格版本，当前估算成本为零；不代表供应商实际账单。
