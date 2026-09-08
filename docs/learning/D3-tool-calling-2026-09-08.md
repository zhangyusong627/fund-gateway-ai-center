# M0 D3 工具调用验证记录

## 验证目标

基于本地 Spring AI 2.0.1，验证 `ToolCallback` 注册、`ToolCallingManager` 执行、未知工具拒绝、调用次数限制和工具异常边界。

## 实验对象

- 工具：`querySyntheticContract`。
- 数据：内存中的合成资方契约，不访问文件、数据库或真实系统。
- 入口：当前请求绑定的 `ToolCallback` 列表，不使用全局注册中心。

## 结果

| 路径 | 结果 |
|---|---|
| 已注册工具且参数合法 | 执行成功，返回合成契约 |
| 未注册工具 | `IllegalStateException` |
| 超过单工具预算 | `ToolCallLimitExceededException` |
| 工具参数非法 | 默认管理器直接抛出 `IllegalArgumentException` |

## 验证命令

```text
/Users/zhangyusong/tools/maven/bin/mvn -B -pl fund-guardian -am test
BUILD SUCCESS
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

## 结论

当前版本提供工具定义解析和工具执行管理；完整的多轮模型循环、失败回灌和应用层终止编排仍由上层实验决定，不能把 `ToolCallingManager` 误认为完整 Agent 循环。
