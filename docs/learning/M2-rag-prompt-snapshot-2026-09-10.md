# M2 RAG 提示词快照

> 文档角色：M2 专题证据。阶段主总结见 [`M2-acceptance-2026-09-10.md`](M2-acceptance-2026-09-10.md)，本文件不单独代表阶段整体结论。

- 执行日期：2026-09-10
- 状态：`ACCEPTED`
- DeepSeek：未调用
- 输入来源：`RagEvidenceQueryService` 通过门禁后的 3 条证据

## System 指令

```text
你是一个基于证据回答问题的助手。只能使用用户提供的证据，不得补造文档没有声明的事实。回答必须使用中文，并严格输出 JSON：{"answer":"...","citations":[{"chunkId":"...","locator":"...","quote":"..."}]}。每条结论至少引用一个证据；引用的 chunkId 和 locator 必须来自证据列表。证据无法支持问题时，answer 必须写“证据不足”，citations 返回空数组。
```

## User 输入

```text
问题：applyAmt 的类型和必填性是什么

证据列表：
[证据 shengheng-consumer:source-v1:117]
来源：文档=shengheng-consumer，版本=source-v1，章节=授信申请，定位=授信申请#117-117
原文：参数名 | 数据类型 | 是否必填 | 说明；当前行：applyAmt | BigDecimal | Y | 授信申请金额；示例：1200.00

[证据 shengheng-consumer:source-v1:94]
来源：文档=shengheng-consumer，版本=source-v1，章节=授信申请，定位=授信申请#94-94
原文：参数名 | 数据类型 | 是否必填 | 说明

[证据 shengheng-consumer:source-v1:118]
来源：文档=shengheng-consumer，版本=source-v1，章节=授信申请，定位=授信申请#118-118
原文：参数名 | 数据类型 | 是否必填 | 说明；当前行：intRate | BigDecimal | Y | 授信申请利率（年利率）示例：若年利率为23.76%,则传入0.2376
```

该快照只验证提示词组装和证据边界，不代表模型已经调用，也不代表模型输出已经通过最终 JSON 门禁。
