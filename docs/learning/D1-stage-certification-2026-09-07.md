# M0-D1 阶段认证记录

- 日期：2026-09-07
- 学习者：雨松
- 主持：Claude Code
- 性质：闭卷检测记录（按 `docs/DAILY_PLAN.md` 三层验收：已运行 / 已理解 / 已独立验证）
- **结论：✅ M0-D1 认证通过（可进入 M0-D2）**

## 一、模型调用核心（三问）—— 通过

| # | 问题 | 结论 | 备注 |
|---|---|---|---|
| 1 | 请求体哪些字段是你设的、哪些是框架默认填的 | ✅ | 补测后通过：`role` 显式设（`Role.USER`）；`thinking` 的 `{"type":"disabled"}` 结构是 SDK 展开；**没设的采样参数（temperature 等）不会出现在请求体里**，由服务端用默认值 |
| 2 | 超时 / 限流时代码卡在哪、抛什么 | ✅ | 补测后通过：读超时 → `SocketTimeoutException`；4xx/5xx → `ProviderStatusErrorHandler.handleError` 抛 `IOException("Provider HTTP xxx")`；`run()` 包装为 `IllegalStateException`，**不传 cause，原始消息/堆栈丢失**，顶层看不出 429/500 |
| 3 | 这次调用花了多少 token | ✅ | 补测后通过：`prompt_tokens`=提示词、`completion_tokens`=回答、`cached=0`=未命中提示词缓存；usage 写进 `result.txt` 且包含在 `response.json` 中 |

运行证据：IDEA 本地两次调用 HTTP 200，证据目录 `docs/learning/D1-call-1788755126516`、`D1-call-1788755267535`（无回显，`response.json` 为原始字节）。

三层状态（模型调用部分）：已运行 ✅ / 已理解 ✅ / 已独立验证 ✅

## 二、项目结构 + POM 讲解与复述 —— 通过

- [x] 完整项目树 + 五模块职责（fund-common / fund-knowledge / fund-guardian / fund-integration / fund-application）
- [x] 根 POM + 模块 POM 结构讲解
- [x] 启动类 `FundGatewayDecisionCenterApplication` 与 `DeepSeekModelCallExperiment` 讲解

复述结论：五模块职责、聚合根 POM + BOM import 的版本管理、`spring-boot-maven-plugin` 决定"谁能 `java -jar`"均答对。

讲解中强调的三个精度点（已现场纠正）：
1. `fund-common` 只放无业务含义的共享标识/错误类型，**不放领域实体和万能工具类**。
2. `spring-ai-bom` 是 `dependencyManagement` import 的**版本清单**，不是父 POM；它不把依赖遗传给子模块，只允许省略版本号。真正做"遗传"的是 `spring-boot-starter-parent`。
3. `fund-integration` 完整流程含"候选契约 → 人工确认"环节，生成代码只是后半段；模块依赖方向是 `fund-application` → 其余四个（单向，防环）。

## 三、次日复习建议

- `HttpStatusCode.isError()` 的定义位置（Spring 框架内，4xx/5xx 判定）与 `ResponseErrorHandler` 回调契约（hasError → handleError 由框架自动调用）
- 提示词缓存：`prompt_cache_hit_tokens` >0 的触发条件（相同前缀近期调用过）
- 请求体 JSON 字段顺序由 SDK 序列化决定，与 builder 书写顺序无关
- BOM（import 版本清单）与 parent POM（遗传依赖/插件）的区别
