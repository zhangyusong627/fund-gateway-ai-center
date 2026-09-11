# FG-ADC 工作约定

- 开场读 `docs/HANDOFF.md` 与全部 ADR；收场在交接日志顶部追加记录，历史不改。任务限当前任务卡；设计冲突交用户裁决。
- 本项目仅用 Java 21 / Maven / 必要 Shell；用户提供的项目材料均按合成数据处理，可在当前项目中复用和迁移。
- 根目录放聚合 `pom.xml`；`fund-common` 仅共享标识、错误和审计关联信息；`fund-knowledge` 管文档、Embedding、RAG 与引用；`fund-guardian` 管指标、诊断 Agent、审批与模拟治理；`fund-console` 是正式统一管理工作台；`fund-integration` 的资方接入助手按 ADR-011 暂缓扩展；`fund-experiments` 只保留本地单点实验和历史回放，不继续承载正式控制台。模块源码遵循 `src/main/java`、`src/test/java`、`src/main/resources`。
- 两个业务域内部使用轻量 DDD 与六边形依赖：入站适配器调用应用用例，应用层编排领域对象，基础设施实现出站端口。禁止把业务实体放入 `fund-common`，禁止跨域直接修改数据库表。
- `docs/adr/ADR-NNN.md` 放冻结决策；`docs/product/` 放 PRD 与范围；`docs/architecture/` 放架构说明和图；`docs/planning/` 放路线图、每日计划和执行协议；`docs/tasks/` 放阶段任务卡；`docs/reviews/` 放阶段评审；`docs/learning/` 放实验与复盘；`docs/HANDOFF.md` 是唯一交接入口。生成的构建输出仅进模块 `target/`；新增目录先补结构约定。清理需授权，不自动删除。
- 版本以 ADR-000 和 BOM 为准，禁止自行升级降级、跳过测试或修改测试/构建来换取通过。
- 构建环境（仅当前 Shell）：`export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"`，`export PATH="$JAVA_HOME/bin:$HOME/tools/maven/bin:$PATH"`。
- 验证：`mvn -B verify`；依赖核对：`mvn -B dependency:tree`。正式工作台启动：`java -jar fund-console/target/fund-console-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1`；历史实验入口按对应 profile 单独运行。
- 本地合成实验应保存完整提示词、模型请求、模型响应、工具调用和门禁结果，便于学习与复盘；认证头、API Key、密码和其他密钥必须在写日志前移除。禁止开启可能输出认证信息的 HTTP debug 日志。
- 所有类和方法提供简短中文注释。使用 Java 8 之后新增的语法或 API 时，在讲解和学习记录中说明引入版本及 Java 8 对应写法。
- 删除、密钥或 .env、CI/CD、数据库 schema/迁移、push/rebase/reset、全局安装和公开发布必须事先获得明确授权。不得自动执行治理动作。
- 已运行、已理解、已独立验证分开记录；AI 代跑不等于学习者掌握。
