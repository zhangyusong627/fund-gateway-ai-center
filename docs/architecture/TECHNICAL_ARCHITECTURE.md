# 资金网关智能守护 Agent：技术架构

> 内部工程代号：FG-ADC 资金网关智能决策中心

## 技术目标

技术架构只服务“资金网关智能守护 Agent”的两条内部链路：可追溯知识库和受控智能守护。采用一个 Maven 仓库、统一管理工作台、知识共享模块、两个独立保留应用和最小本地基础设施，不建设通用 AI 平台。

## 技术架构图

![FG-ADC 技术架构图](technical-architecture.svg)

## 模块职责

- `fund-knowledge`：文档解析、切分、本地 BGE Embedding、pgvector 检索、引用和评测。
- `fund-integration`：暂缓的既有 SPI/资方接入助手，保持现状，不作为本期新增运行边界。
- `fund-guardian`：指标消费、窗口、规则、风险、诊断快照、Agent、门禁、审核和模拟治理。
- `fund-guardian` 的 Agent 上下文由 `conversationId`、`diagnosticTaskId`、有限会话消息、诊断快照、RAG 引用和工具轨迹组成；会话记忆与长期案例记忆不放入 `fund-common`。
- Prompt 使用 classpath 版本资源；Agent 由应用层实现有界循环，Java 控制工具白名单、权限、轮次、超时和终止状态。
- `fund-console`：当前统一管理后台、REST 入站接口和跨模块只读聚合，并装配本期知识库、评测、守护回放、Agent、审批和审计闭环；长期领域规则仍应归属对应能力模块。
- `fund-common`：通用标识、错误和审计关联信息，不放领域实体。
- `fund-experiments`：历史实验与回放入口，不进入最终业务边界。

知识库和智能守护内部采用轻量 DDD 与六边形依赖：入站适配器调用应用用例，应用层编排领域对象，基础设施实现出站端口。

## 核心数据流

```mermaid
flowchart LR
 D[文档版本] --> P[解析与切分] --> E[BGE Embedding] --> V[pgvector]
 V --> R[Top-K与引用]
 R --> K[诊断知识证据]
 M[Mock指标] --> Q[Redpanda（可选，默认关闭）] --> W[窗口与特征] --> F[风险与降频]
 F --> S[诊断快照]
 R --> S
 S --> A[Tool Calling与DeepSeek] --> G[Java最终门禁] --> O[报告或人工审核]
```

## 基础设施

- PostgreSQL 16 + pgvector：一个实例，使用 `knowledge`、`integration`、`guardian` 三个 Schema；`integration` 目前是暂缓能力的预留边界。
- Redpanda：只承载 `guardian.metric-events.v1` 与 `integration.contract-published.v1`。
- DeepSeek：候选事实抽取和诊断推理；不负责数值计算、权限和状态转换。
- 本地 BGE：文档与查询使用同一模型、512 维和归一化配置。
- Docker Compose：启动 `fund-console`、`fund-guardian`、`fund-integration`、PostgreSQL/pgvector 和 Redpanda；默认完整演示入口是 `fund-console`，指标 Kafka 消费链路可单独开启验证。
- 本期不使用 Redis；幂等由 PostgreSQL 唯一约束保证，冷却与频控由应用逻辑和持久化时间字段完成。

## 数据所有权

- `knowledge`：文档、分块、向量、来源、索引版本和检索评测。
- `integration`：既有接入任务、候选规范、审核和发布版本，暂缓扩展。
- `guardian`：聚合窗口、风险、诊断快照、报告、审核和模拟治理。
- 审计记录随所属域保存，并通过 `traceId` 关联。
- 原始指标由 Redpanda 有限期保留，不永久复制全部遥测。

## AI 工程边界

Java 先准备结构化事实和主要证据，再允许模型通过白名单工具补充只读调查。模型输出必须映射为 Java 对象，并经过结构、证据、规则、权限和中文门禁。模型与确定性规则发生硬冲突时由人工裁决。

## 部署与验证

本地使用 Mock 指标和用户提供文档，不连接真实监控与资方系统。测试覆盖正常、单指标、多指标、重复风险、缺证、非法引用、越权、硬冲突及依赖故障。最终报告本机实测结果，不宣称生产容量或高可用。
