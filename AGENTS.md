# FG-ADC 工作约定

- 开场读 `docs/HANDOFF.md` 与全部 ADR；收场在交接日志顶部追加记录，历史不改。任务限当前任务卡；设计冲突交用户裁决。
- 本项目仅用 Java 21 / Maven / 必要 Shell；用户提供的项目材料均按合成数据处理，可在当前项目中复用和迁移。
- 根目录放聚合 `pom.xml`；`fund-common` 仅共享标识与错误；`fund-knowledge` 管契约、证据与查询；`fund-guardian` 和 `fund-integration` 是独立应用链路；`fund-application` 负责单进程装配。模块源码遵循 `src/main/java`、`src/test/java`、`src/main/resources`。
- `docs/adr/ADR-NNN.md` 放冻结决策；`docs/SCOPE.md` 放范围验收；`docs/ARCHITECTURE.md` 放架构；`docs/learning/DN-*` 放脱敏实验与复盘。生成的构建输出仅进模块 `target/`；新增目录先补结构约定。清理需授权，不自动删除。
- 版本以 ADR-000 和 BOM 为准，禁止自行升级降级、跳过测试或修改测试/构建来换取通过。
- 构建环境（仅当前 Shell）：`export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"`，`export PATH="$JAVA_HOME/bin:$HOME/tools/maven/bin:$PATH"`。
- 验证：`mvn -B verify`；依赖核对：`mvn -B dependency:tree`；启动：`java -jar fund-application/target/fund-application-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1`。
- D1 实验：通过环境变量传 `DEEPSEEK_API_KEY`、`D1_PROMPT`，以 `--spring.profiles.active=d1 --spring.main.web-application-type=none` 启动 jar。提示仅在内存，不回显；报文写入前删除认证信息并替换消息内容。禁止 HTTP debug/完整请求日志。
- 删除、密钥或 .env、CI/CD、数据库 schema/迁移、push/rebase/reset、全局安装和公开发布必须事先获得明确授权。不得自动执行治理动作。
- 已运行、已理解、已独立验证分开记录；AI 代跑不等于学习者掌握。
