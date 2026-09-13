# M5 失败分支与任务恢复固定验收

- 执行日期：2026-09-13
- 验收入口：`EvidenceAcceptanceFixedSetTest`、`GuardianFailureAcceptanceTest`、`M5DatabaseDiagnosisExperimentTest`
- 运行命令：`mvn -B -pl fund-knowledge,fund-guardian,fund-experiments -am -Dtest=EvidenceAcceptanceFixedSetTest,GuardianFailureAcceptanceTest,M5DatabaseDiagnosisExperimentTest -Dsurefire.failIfNoSpecifiedTests=false test`

## 固定覆盖

| 场景 | 固定断言 | 证据边界 |
|---|---|---|
| 证据不足拒答 | 缺少 `riskScore` 时门禁拒绝，并返回缺失关键词 | 只验证证据是否可交给生成层，不代表自然语言答案正确 |
| 工具参数非法 | 既有 `SyntheticContractQueryToolTest` 验证非法参数抛出异常；固定入口另验证空证据关键词拒绝 | 不调用外部模型或真实工具服务 |
| 模型失败 | M5 模型调用抛异常时任务状态回写 `FAILED`，失败文件只记录异常类型 | 不记录异常消息，避免把潜在敏感内容写入证据 |
| 审批拒绝 | 任务进入 `REJECTED` 终态，并保留审核人、动作和原因 | 审核人仍是合成的项目所有者，不等同于生产身份系统 |
| 重复请求 | 相同创建键返回同一任务，相同审批操作号不追加时间线 | 这是单请求幂等，不是跨系统幂等保证 |
| 任务恢复 | 从完整聚合状态恢复后保留审批、模拟记录和已完成操作号 | 当前是领域恢复测试，尚未替代真实 PostgreSQL 重启回放 |

## 本次结果

- `fund-knowledge`：3 个固定证据门禁测试通过。
- `fund-guardian`：4 个固定失败/审批/幂等/恢复测试通过。
- `fund-experiments`：1 个模型失败状态回写测试通过。
- 合计 8 个新增固定入口测试通过，未修改既有测试断言、依赖版本或数据库结构。
- 项目级 `mvn -B -DargLine=-javaagent:<本机 Maven 缓存中的 byte-buddy-agent-1.18.11.jar> verify` 通过：`fund-knowledge` 22 个（跳过 2 个外部数据库测试）、`fund-guardian` 35 个、`fund-integration` 14 个、`fund-console` 5 个、`fund-experiments` 6 个。
- 不带该临时 agent 的标准命令在当前受限进程环境中会被既有 Mockito 测试的 Byte Buddy 自附加失败阻断；该命令差异未写入项目构建配置。

## 未覆盖项

- 尚未用真实 PostgreSQL 容器执行一次“进程重启后读取 `diagnosis_workflow_tasks` 及其时间线/审批/模拟记录”的集成测试；已有 JDBC 行映射和领域恢复单元测试不能替代该证据。
- 尚未对真实 DeepSeek 的超时、HTTP 4xx/5xx 和连接中断做在线回归；不应为此批量消耗模型调用预算。
- RAG Recall@K、MRR 和引用命中率仍以已有版本化数据库评测集及历史运行记录为准，本入口不新增或伪造这些指标。
