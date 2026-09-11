# M4 Redpanda 指标消息回放

- 日期：2026-09-11
- 镜像：`redpandadata/redpanda:v24.3.6`
- Topic：`guardian.metric-events.v1`
- 生产消息：20 条（正常 18 条、风险 2 条）
- 消费组：`fund-guardian-m4`

## 执行结果

1. Colima 重启后恢复 Docker daemon 网络，成功拉取 Redpanda 镜像。
2. 启动 Redpanda 容器 `fund-redpanda-m4`，Kafka API 外部端口 `19092`。
3. 使用 `m4,m4-producer` profile 启动 Spring Boot，Mock 生产者发送 20 条消息。
4. `rpk topic list` 能看到目标 Topic；`rpk group describe fund-guardian-m4` 显示 `TOTAL-LAG=0`，说明消费者已处理完消息。
5. `rpk topic consume` 能读取指标 JSON，字段包含 `eventId`、请求数、错误数、延迟样本、线程池和 GC 指标。

## 边界

- 本次只证明 Redpanda、Mock 生产者、Kafka 消费入口和窗口聚合链路可连通。
- 风险规则、风险指纹、冷却和 PostgreSQL 幂等尚未接入。
