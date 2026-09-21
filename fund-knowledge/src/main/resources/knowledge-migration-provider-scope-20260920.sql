-- 将历史知识分片绑定到文档对应的 NYXJ 资方身份。
-- 本迁移只修正合成数据的归属，不改变表结构；执行前由人工确认目标环境。
update knowledge.knowledge_chunks
set institution = 'NYXJ', updated_at = now()
where institution in ('synthetic-source', 'synthetic-provider');
