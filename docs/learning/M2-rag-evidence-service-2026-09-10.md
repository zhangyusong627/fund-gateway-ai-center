# M2 RAG 证据编排入口验证

- 执行日期：2026-09-10
- 服务：`RagEvidenceQueryService`
- 处理链路：查询向量 → 混合检索 → 证据接受门禁 → 带引用证据或证据不足
- DeepSeek：未调用

## 通过场景

问题：`applyAmt` 的类型和必填性是什么？

- 状态：`ACCEPTED`
- 返回证据：3 条
- 第一条引用：`授信申请#117-117`

## 拒绝场景

问题：文档是否声明 `riskScore`？

- 状态：`INSUFFICIENT_EVIDENCE`
- 返回证据：0 条
- 缺失关键词：`riskScore`

## 结论

后续答案生成环节只能读取 `ACCEPTED` 响应中的 `EvidenceCitation`。证据不足时服务返回空证据和缺失项，阻断模型生成，避免模型根据相似文本自行补猜。
