# M4 运行 profile 完整链路回放（2026-09-11）

## 目的

验证 `m4 + m4-producer + m4-runtime` 启动时，Redpanda 指标消息能经过 Spring Kafka 消费者、10 秒窗口聚合、风险规则/指纹/冷却编排，并写入 PostgreSQL 的 `guardian` 表。

## 执行环境

- Java：21
- Spring Boot：4.1.1
- Redpanda：`fund-redpanda-m4`，Kafka 地址 `localhost:19092`
- PostgreSQL + pgvector：`fund-integration-pgvector`，JDBC 地址 `localhost:55432/fund_integration`
- 启动 profile：`m4,m4-producer,m4-runtime`
- 合成消息：18 条正常指标、2 条风险指标，共 20 条

## 执行命令

```shell
mvn -B -pl fund-experiments -am package -DskipTests
GUARDIAN_METRIC_CONSUMER_AUTO_START=true \
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 \
java -jar fund-experiments/target/fund-experiments-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=m4,m4-producer,m4-runtime
```

## 结果

- 首次启动发现实验启动类没有显式导入 `MetricProcessingConfiguration`，导致窗口聚合器未注册；补充显式配置导入后重新构建。
- 第二次启动成功：Spring Boot 正常启动，Kafka 消费组 `fund-guardian-m4` 成功订阅 `guardian.metric-events.v1`，Mock Producer 发送 20 条消息。
- PostgreSQL 在本次回放新增 1 条风险事件和 1 条诊断任务；数据库总量为 3 条风险事件、4 条诊断任务，历史记录未清理。
- 本次运行证明链路已接通：`Redpanda → MetricEventConsumer → MetricWindowAggregator → RiskDiagnosisCoordinator → PostgreSQL`。

## 验收判断

通过。M4 运行 profile 已能完成真实消息消费和数据库落库；首次装配缺陷已修复并纳入待提交变更。
