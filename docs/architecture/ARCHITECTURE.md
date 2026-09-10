# 架构索引

当前架构以 ADR-008、ADR-009 和 ADR-010 为准。本文件只提供入口。

- [业务架构](BUSINESS_ARCHITECTURE.md)：三个业务能力、两个独立业务域、共享知识底座和业务边界。
- [技术架构](TECHNICAL_ARCHITECTURE.md)：Java 模块、RAG、Agent、Redpanda、PostgreSQL/pgvector 和 Docker 拓扑。
- [产品需求](../product/PRD.md)：核心交付、有条件扩展、明确不做和最终验收。
- [范围与验收](../product/SCOPE.md)：一页纸范围边界。
- [执行路线图](../planning/ROADMAP.md)：截至 2026-09-30 的唯一里程碑与依赖顺序。

核心顺序：先完成知识底座，再完成资方接口规范，然后建设实时指标链路，最后将 RAG 与受控 Agent 接入诊断。代码生成和 Redis 不属于本期核心交付。
