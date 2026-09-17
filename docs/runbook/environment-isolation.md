# 本地环境隔离运行手册

## 环境选择

应用实例通过环境变量固定目标数据库：

```bash
CONSOLE_ENVIRONMENT=dev CONSOLE_DB_NAME=fund_dev docker compose up -d fund-console
CONSOLE_ENVIRONMENT=acceptance CONSOLE_DB_NAME=fund_acceptance docker compose up -d fund-console
CONSOLE_ENVIRONMENT=baseline CONSOLE_DB_NAME=fund_baseline docker compose up -d fund-console
```

同一个运行中的实例不提供动态切换数据库。`/api/console/status` 的 `environment` 字段和页面环境标识必须与启动参数一致。

## 数据库初始化

三个数据库共用同一个 PostgreSQL 容器，但各自执行 `fund-knowledge` 和 `fund-guardian` 的初始化 DDL。初始化脚本只创建缺失对象，不删除已有数据。`fund_acceptance` 用于从空数据开始的验收；历史数据不得复制到其中。

## 验收要求

1. 在 `acceptance` 中登记合成文档、执行索引、运行 RAG 评测。
2. 在同一环境运行智能守护和受控 Agent/记忆验收。
3. 查询 `/api/console/status`，确认环境是 `acceptance`。
4. 在 `baseline` 中只查看历史结果，不把它当作 acceptance 的通过证据。
