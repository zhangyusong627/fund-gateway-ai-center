# M0 D5 最小契约与诊断评测记录

## 验证目标

定义一条可追溯的合成资方契约和一个诊断评测样例，为后续 M1 诊断 Agent 提供固定输入和证据要求。

## 最小契约字段

`providerId`、`interfaceId`、`source`、`version`、`effectiveAt`、`qpsLimit`、`timeoutMs`、`idempotencySupported`。

## 诊断样例

- 现象：超时尖峰、资方响应延迟。
- 预期结论：资方接口响应超时风险。
- 必需证据：已发布契约中的 `timeoutMs` 和运行基线中的 `p95`。

## 范围

本次只定义不可变契约记录和诊断评测输入，不做数据库、契约发布流程、规则基线或 Agent 集成。
