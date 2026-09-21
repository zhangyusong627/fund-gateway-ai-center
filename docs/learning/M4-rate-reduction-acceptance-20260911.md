# M4 降频验收

- 输入：Redpanda `guardian.metric-events.v1`
- 消费消息：20 条
- 风险事件：1 条风险指纹事实
- 诊断任务：1 条
- 结果：指标消息数显著大于诊断任务数（20:1）

执行输出：

```text
M4 降频验收：metricMessages=20，riskEvents=1，diagnosticTasks=1，taskCreated=false
```

`taskCreated=false` 表示本次模拟发现该风险窗口已有幂等任务；数据库中最终仍只有 1 条诊断任务，重复模拟没有新增任务。
