# M0 D4 最小向量检索验证记录

## 验证目标

用 Java 合成文档和预先构造的向量验证余弦相似度、Top-K 排序和向量维度边界。样本文本覆盖中文、英语单词、表格和流程图文本化形式。

## 范围

- 做：内存向量检索、相似度排序、维度错误处理。
- 不做：Embedding API、PostgreSQL、pgvector、文档解析和生产索引。

## 结果

查询契约向量时，契约文本排名第一，表格文本排名第二；向量维度不一致时抛出明确异常。

## 验证命令

```text
/Users/zhangyusong/tools/maven/bin/mvn -B -pl fund-knowledge -am test
```
