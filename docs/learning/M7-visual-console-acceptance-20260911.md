# M7 可视化演示控制台验收（2026-09-11）

## 业务目标

为本地演示提供统一入口，让学习者可以直接观察 RAG 的召回证据与分数，也可以回放智能守护指标并观察消息降频、确定性规则、诊断任务和模型门禁。

## 运行入口

- 地址：`http://localhost:18080/`
- RAG：本地 `BAAI/bge-small-zh-v1.5` 生成 512 维向量，PostgreSQL 16 + pgvector 执行向量与关键词混合检索。
- 智能守护：生成 100、1000 或 10000 条合成指标，展示十秒窗口、风险规则、风险指纹、六十秒冷却和诊断任务。
- DeepSeek：只有用户打开页面开关且已经产生诊断任务时才调用；一次页面回放最多调用一次。页面展示完整提示词、原始响应、结构化报告和 Java 门禁结果。

## 构建与启动

控制台镜像只封装已经通过 Maven 验证的 jar 和本地 BGE 模型，避免 Docker 构建阶段依赖公网 Maven 仓库。

```shell
mvn -B clean verify
source ~/.zshrc >/dev/null 2>&1
docker compose build fund-console
docker compose up -d fund-console
```

## 验收结果

- 全量 Maven：46 项测试通过，0 失败；knowledge 12、guardian 15、integration 14、experiments 5。
- Compose：PostgreSQL 与 Redpanda healthy；`fund-integration`、`fund-guardian`、`fund-console` 均正常运行。
- RAG：10 个分片，问题“授信申请金额字段是否必填，数据类型是什么？”返回 `ACCEPTED`；Top-1 为 `applyAmt / BigDecimal / 必填`，融合分约 0.8988。
- 智能守护：10000 条消息形成 10 个聚合窗口、10 个风险窗口和 2 个诊断任务，8 次被冷却抑制，消息/任务比为 5000。
- DeepSeek：实际调用 1 次，返回固定中文 JSON；Java 门禁结果为 `ACCEPTED`，页面可展开查看完整提示词与原始响应。
- 浏览器：苹果 App 风格布局、两个页签和三个核心交互均通过；未发现浏览器控制台错误。

## 验收结论

通过。控制台提供了可视化触发入口，也能展示 RAG 检索质量和智能守护 Agent 从指标到模型门禁的完整结果。
