# 学习与验收证据索引

本目录保存项目学习过程、真实模型联调、阶段验收、原始请求响应和模拟结果。这里记录的是“实际运行和验证过什么”，不是产品需求或架构规范。

## 阶段主总结

| 阶段 | 主总结 | 详细证据 |
|---|---|---|
| M0 / D1-D5 | [D1 阶段认证](D1-stage-certification-2026-09-07.md) | D1-D5 实验记录、HTTP 和 JSON 文件 |
| M1 | [M1-D1 阶段验收](M1-D1-acceptance.md) | `M1-real-call-*` 原始模型与工具证据 |
| M2 | [M2 阶段验收](M2-acceptance-2026-09-10.md) | 分片、Embedding、pgvector、检索、评测和 RAG 联调记录 |
| M3 | [M3 审核闭环验收](M3-review-e2e-20260911/README.md) | 候选提交、审批、退回、拒绝和发布响应 |
| M4 | [M4 运行 profile 模拟](M4-runtime-profile-e2e-20260911.md) | Redpanda、降频、风险落库和模拟记录 |
| M5 | [M5 失败与恢复验收](M5-failure-and-recovery-acceptance-2026-09-13.md) | 失败分支和任务恢复记录 |
| M7 | [M7 控制台持久化验收](M7-console-persistence-acceptance-20260912.md) | 控制台 Docker 和 PostgreSQL 模拟记录 |
| M8 | [M8 记忆与 Agent 验收](M8-memory-agent-acceptance-20260914.md) | 记忆隔离、摘要、Agent 恢复和长期案例证据 |

## 证据约定

- 阶段主总结负责给出结论；同阶段的其他 Markdown 是专题证据。
- `*-real-call-*` 目录保存原始请求、响应、工具结果和状态文件，不改写成“看起来更成功”的结果。
- 真实模型调用记录必须移除认证头、API Key、密码和其他密钥。
- “已运行”“已理解”“已独立验证”分别记录，AI 代跑不等于学习者掌握。
- 学习证据不得被当作生产容量、真实业务接入或生产上线证明。
