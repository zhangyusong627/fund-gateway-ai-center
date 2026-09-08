# D4 在线检索验证记录

## 验证目标

验证 Java 本地 BGE Embedding 生成查询向量后，pgvector 能否完成相似度计算和 Top-K 返回。

## 验证链路

```text
中文查询 → BGE ONNX Runtime → 512 维向量 → PostgreSQL pgvector → Top-K
```

## 结果

- 模型：`BAAI/bge-small-zh-v1.5`
- 运行方式：Java + DJL + ONNX Runtime，本地模型文件
- 向量维度：512
- 数据库：PostgreSQL 16 + pgvector 0.8.6
- 查询：`授信申请需要填写申请金额`
- Top-K=2：返回申请金额字段（0.7959）和申请期限字段（0.7036）

## 范围

本记录只覆盖 RAG 在线检索阶段。文档切片、批量离线建索引和 DeepSeek 生成不属于本次验证。
