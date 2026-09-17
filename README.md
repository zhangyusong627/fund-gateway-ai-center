# FG-ADC 资金网关智能决策中心

FG-ADC（Fund Gateway AI Decision Center）是一个 Java-first 的 AI 大模型应用工程项目，用合成数据演示资金网关场景下的知识库、RAG、受控诊断 Agent、记忆管理、模型审计和模拟治理闭环。

项目重点不是建设通用 AI 平台，而是验证一条可追溯、可评测、可控的业务链路：

```text
资方文档 → 解析分片 → BGE Embedding → PostgreSQL/pgvector → RAG 证据
                                                               ↓
合成指标 → 风险识别 → 诊断任务 → Agent 调查 → Java 门禁 → 审核/模拟治理
```

## 当前范围

当前交付包含两个核心业务域：

- 资方知识库：文档登记、版本管理、解析、分片、向量索引、混合检索、来源引用、分片浏览和 RAG 评测。
- 智能守护：合成指标、窗口与规则、风险指纹、去重冷却、诊断任务、RAG、白名单只读工具、DeepSeek、记忆管理、Agent 执行状态、Java 门禁、人工审批和模拟治理。

`fund-integration` 中已有的资方接入助手暂缓扩展，仅保留现状作为后续参考；本期不将其作为主业务链路，也不接入真实资方系统、真实监控系统或生产治理系统。

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言与构建 | Java 21、Maven |
| 应用框架 | Spring Boot 4.1.1、Spring AI 2.0.1 |
| 知识库 | PostgreSQL 16、pgvector、本地 BGE `BAAI/bge-small-zh-v1.5` |
| 消息 | Redpanda（Kafka 协议） |
| 大模型 | DeepSeek，可选调用 |
| 部署 | Docker Compose |
| 数据 | 合成文档、合成指标、合成诊断场景 |

模型只负责候选事实、候选根因和建议；数值计算、规则、权限、状态转换、工具白名单、门禁和治理动作由 Java 与人工控制。

## 模块结构

| 模块 | 职责 | 当前定位 |
|---|---|---|
| `fund-common` | 通用标识、错误和审计关联信息 | 共享基础模块，不放业务实体 |
| `fund-knowledge` | 文档、分片、Embedding、pgvector、RAG、引用和评测 | 知识能力模块 |
| `fund-guardian` | 指标、窗口、风险、诊断、Agent、记忆、门禁、审核和模拟治理 | 智能守护能力与独立应用 |
| `fund-integration` | 既有 SPI/资方接入助手 | 暂缓能力，当前主要是内存实现 |
| `fund-console` | 统一管理后台、REST API、跨模块聚合和当前演示编排 | 默认完整演示入口 |
| `fund-experiments` | 单点实验和历史回放 | 不属于正式控制台边界 |

当前 Docker Compose 会启动 `fund-console`、`fund-guardian`、`fund-integration`、PostgreSQL 和 Redpanda。默认完整业务演示通过 `fund-console` 完成；`fund-guardian` 的 Redpanda 指标消费者默认关闭，需要单独开启时再配置 `GUARDIAN_METRIC_CONSUMER_AUTO_START=true`。

## 本地环境隔离

同一个 PostgreSQL 实例内维护三个数据库：`fund_dev` 用于日常开发，`fund_acceptance` 用于从空环境走完整验收，`fund_baseline` 保存历史基线。每个数据库继续使用 `knowledge` 和 `guardian` 领域 schema。应用启动时通过 `CONSOLE_ENVIRONMENT` 与 `CONSOLE_DB_NAME` 固定目标，控制台不会在运行时切换数据库；状态接口和首页会显示当前环境。详细操作见 [本地环境隔离运行手册](docs/runbook/environment-isolation.md) 和 [ADR-017](docs/adr/ADR-017-environment-isolation.md)。

## 快速开始

### 前置条件

- macOS 或 Linux
- JDK 21
- Maven 3.9+
- Docker Desktop 或 Colima
- 本地 BGE 模型目录：`models/bge-small-zh-v1.5`，或通过 `BGE_MODEL_PATH` 指向已下载模型

DeepSeek 密钥必须放在仓库外，Compose 使用的文件为：

```text
$HOME/.config/fund-gateway-ai-center/deepseek.env
```

文件内容示例：

```dotenv
DEEPSEEK_API_KEY=your-key
```

该文件权限应为 `600`。不要把密钥写入仓库、`.env`、代码、镜像或日志。

### 构建与启动

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/tools/maven/bin:$PATH"

mvn -B verify
docker compose up -d --build
```

启动后访问：

- 统一管理工作台：[http://127.0.0.1:18080](http://127.0.0.1:18080)
- 控制台状态：[http://127.0.0.1:18080/api/console/status](http://127.0.0.1:18080/api/console/status)
- Guardian 健康检查：[http://127.0.0.1:18082/actuator/health](http://127.0.0.1:18082/actuator/health)
- Integration 健康检查：[http://127.0.0.1:18081/actuator/health](http://127.0.0.1:18081/actuator/health)

也可以只启动已打包的正式工作台：

```bash
java -jar fund-console/target/fund-console-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1
```

### 控制台使用顺序

1. 在“文档管理”上传合成文档并登记版本。
2. 在“索引任务”执行解析、分片和向量索引，确认集合为 `PUBLISHED`。
3. 在“检索实验室”查看 Top-K 结果、分数、分片和来源定位。
4. 在“RAG 评测”创建评测集；标准分片和定位信息可从“浏览分片”中选择。
5. 在“智能守护”执行合成指标回放，查看风险、诊断快照、模型调用和门禁结果。
6. 对需要人工确认的诊断执行审批；治理操作只生成模拟记录。
7. 在“模型成本与审计”按币种查看 Token、调用、价格版本和预估费用。

## 关键 API

控制台基础路径为 `/api/console`，主要入口包括：

| API | 用途 |
|---|---|
| `GET /overview` | 运行总览 |
| `GET /status` | 依赖和技术基线状态 |
| `POST /documents` | 上传并登记文档 |
| `POST /index-tasks/{taskId}/execute` | 执行索引任务 |
| `GET /rag/chunks` | 浏览已发布文档分片 |
| `POST /rag/query` | 执行 RAG 检索 |
| `POST /rag/evaluation-sets` | 创建评测集 |
| `POST /rag/evaluation-sets/{setId}/runs` | 执行评测集 |
| `POST /guardian/simulate` | 执行智能守护回放 |
| `POST /guardian/agent/replay` | 启动受控 Agent 回放 |
| `GET /audit/model-costs` | 查看模型成本汇总 |

实际请求字段以 `fund-console` 控制器和页面为准。

## 数据模型

数据模型按业务域划分：

- `knowledge`：文档、解析元素、分片、向量、索引任务、评测和检索审计。
- `guardian`：风险事件、诊断任务、审批、模拟治理、模型调用审计、会话记忆、长期案例记忆和 Agent 执行状态。
- `integration`：当前属于暂缓能力的预留边界，不应据此理解为已经完成的持久化接入闭环。

模型文件和 ER 图：

- [DBML 数据模型](docs/architecture/data-model.dbml)
- [Knowledge ER 图](docs/architecture/data-model-knowledge-er.svg)
- [Guardian ER 图](docs/architecture/data-model-guardian-er.svg)

## 架构与项目文档

- [业务架构](docs/architecture/BUSINESS_ARCHITECTURE.md)
- [技术架构](docs/architecture/TECHNICAL_ARCHITECTURE.md)
- [架构总入口](docs/architecture/ARCHITECTURE.md)
- [产品需求与验收](docs/product/PRD.md)
- [范围边界](docs/product/SCOPE.md)
- [路线图](docs/planning/ROADMAP.md)
- [执行协议](docs/planning/EXECUTION_PROTOCOL.md)
- [ADR 决策目录](docs/adr/)
- [交接入口](docs/HANDOFF.md)
- [文档索引](docs/README.md)

## 验证口径

标准全量验证：

```bash
mvn -B verify
```

依赖核对：

```bash
mvn -B dependency:tree
```

文档或前端静态修改后，还应执行：

```bash
git diff --check
```

验证结果只代表本机合成数据场景的可复现性，不代表生产容量、高可用性或真实资方系统接入能力。

## 设计边界

- 不使用真实业务数据、真实资方接口或真实生产监控数据。
- 不把运行指标写入 RAG；文档正文和来源进入知识库，运行事实进入结构化诊断链路。
- 不让模型直接发布接口规范、改变状态、越过权限或执行写操作。
- 不引入 Redis、Flink、Kubernetes 或微服务平台。
- 任何数据库 Schema、密钥、CI/CD、部署到生产、Git push 或历史删除操作，都必须单独确认并记录。
