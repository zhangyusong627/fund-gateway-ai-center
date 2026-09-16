# 资金网关智能守护 Agent：文档索引

> 内部工程代号：FG-ADC 资金网关智能决策中心

`docs/HANDOFF.md` 是跨会话交接入口；本页按文档用途组织其他材料。

## 项目入口

- [`../README.md`](../README.md)：项目整体介绍、快速启动、模块和当前边界。
- [`HANDOFF.md`](HANDOFF.md)：当前状态、唯一动作和跨会话交接；历史记录只追加。

## 产品与范围

- [`product/PRD.md`](product/PRD.md)：核心业务需求、功能点、验收和明确不做项。
- [`product/SCOPE.md`](product/SCOPE.md)：一页纸范围边界。

## 架构

- [`architecture/ARCHITECTURE.md`](architecture/ARCHITECTURE.md)：架构总入口。
- [`architecture/BUSINESS_ARCHITECTURE.md`](architecture/BUSINESS_ARCHITECTURE.md)：资方知识库、智能守护及暂缓业务边界。
- [`architecture/TECHNICAL_ARCHITECTURE.md`](architecture/TECHNICAL_ARCHITECTURE.md)：模块、AI、消息、数据和部署拓扑。
- `architecture/*.svg`：可视化架构图源文件。

## 计划与任务

- [`planning/ROADMAP.md`](planning/ROADMAP.md)：唯一里程碑和依赖顺序。
- [`planning/DAILY_PLAN.md`](planning/DAILY_PLAN.md)：每日安排。
- [`planning/EXECUTION_PROTOCOL.md`](planning/EXECUTION_PROTOCOL.md)：开工、验证、收工和学习验收机制。
- [`planning/PRODUCTIONIZATION-FOCUS.md`](planning/PRODUCTIONIZATION-FOCUS.md)：生产化增强的 P0/P1 范围与暂不做项。
- [`tasks/README.md`](tasks/README.md)：任务卡索引、当前主线和历史任务卡说明。
- [`tasks/M8-MEMORY-AGENT-REQUIREMENTS.md`](tasks/M8-MEMORY-AGENT-REQUIREMENTS.md)：记忆管理与受控多轮 Agent 任务卡。

## 决策、评审与学习证据

- [`adr/README.md`](adr/README.md)：ADR 决策索引和当前有效/历史决策说明。
- `reviews/`：阶段风险评审和设计评审。
- [`learning/README.md`](learning/README.md)：阶段主总结、详细证据和学习验收约定。
- [`learning/M7-visual-console-acceptance-20260911.md`](learning/M7-visual-console-acceptance-20260911.md)：本地可视化控制台的构建、运行和端到端验收证据。

## 面试准备

- `interview/`：个人求职面试题库。依据简历、当前项目实现和图灵面试题库整理，用于个人口述演练；**不属于项目交付物，也不作为项目证据使用**。
- [`interview/张雨松-Java-AI-面试题库.md`](interview/张雨松-Java-AI-面试题库.md)：三部分共 274 题——知识题库 205 题（从图灵题库 1431 篇逐篇提炼）+ 项目深挖 45 题 + 求职软技能与反问 24 题。资金网关业务与生产经历部分按用户要求不纳入，由本人自行准备。
