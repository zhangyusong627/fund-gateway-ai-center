# M2 RAG 真实 DeepSeek 联调摘要

> 文档角色：M2 专题证据。阶段主总结见 [`M2-acceptance-2026-09-10.md`](M2-acceptance-2026-09-10.md)，本文件不单独代表阶段整体结论。

- 执行目录：`M2-rag-real-call-1789047273238`
- 请求模型：`deepseek-v4-flash`
- 请求方式：Spring AI 2.0.1 `DeepSeekApi`，同步非流式调用
- 触发问题：`applyAmt 的类型和必填性是什么`
- 证据门禁：通过
- 模型调用：成功
- 响应 JSON 结构门禁：通过
- 引用白名单门禁：通过，接受 1 条引用
- Token 用量：prompt 365，completion 94，total 459

## 模型返回结论

模型返回：`applyAmt` 的数据类型为 `BigDecimal`，是否必填为 `Y`（必填），说明为授信申请金额，示例为 `1200.00`。

引用：`shengheng-consumer:source-v1:117`，定位 `授信申请#117-117`。

## 重要观察

请求中明确填写了 `deepseek-v4-flash`，服务端响应的 `model` 字段返回 `deepseek-flash`。这次调用成功，但后续需要把“请求模型标识”和“服务端实际返回模型标识”分别记录，不能只看请求参数就认定服务端实际使用了同名模型。

## 证据文件

- `request.json`：完整原始请求，不含 Authorization 头
- `response.json`：完整原始响应
- `validation.json`：Java 结构和引用门禁结果
- `status.txt`：模型、状态和引用数量

本次没有把 API Key 写入代码、请求文件、响应文件或日志。
