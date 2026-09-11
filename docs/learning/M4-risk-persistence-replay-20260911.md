# M4 风险落库幂等回放

- Profile：`m4-persistence`
- 数据库：本地 PostgreSQL `fund_integration`
- 输入：同一风险窗口重复处理两次

## 结果

```text
firstTask=true，secondTask=false，riskEvents=1，diagnosticTasks=1
```

第一次风险处理创建诊断任务，第二次因冷却和数据库唯一约束被抑制；风险事件保留一条事实记录。
