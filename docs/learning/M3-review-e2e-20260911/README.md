# M3 审核闭环端到端验收

- 日期：2026-09-11
- 环境：本地 Spring Boot 演示应用，端口 18080
- 数据：合成候选接口规范，不调用 DeepSeek，不连接真实资方

## 验收步骤

1. 提交 `candidate-e2e-approve-001`，返回 200，状态进入 `PENDING_REVIEW`。
2. 提交 `candidate-e2e-return-001`，返回 200，状态进入 `PENDING_REVIEW`。
3. 提交 `candidate-e2e-reject-001`，返回 200，状态进入 `PENDING_REVIEW`。
4. 通过第 1 个候选，返回 200，生成不可变 `v1`。
5. 退回第 2 个候选，返回 200，状态为 `RETURNED`。
6. 拒绝第 3 个候选，返回 200，状态为 `REJECTED`。
7. 查询 `synthetic-provider / credit-application`，返回 200，只看到已发布 `v1`。
8. 重复通过第 1 个候选，返回 409，证明审核状态不能重复操作。

## 结果

全部步骤通过。原始请求/响应和 HTTP 状态保存在本目录；发布结果包含来源文档版本、chunkId、章节定位和审核人。
