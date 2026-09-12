-- 合成基线评测集；仅在对应文档版本已发布时执行，题目与标准 chunkId 绑定。
insert into knowledge.rag_evaluation_sets
    (set_id, name, collection_name, document_id, document_version, top_k)
select '11111111-1111-1111-1111-111111111111'::uuid, '授信申请接口合成基线',
       'fund-gateway-contracts', 'shengheng-api', 'v1', 3
where exists (select 1 from knowledge.knowledge_chunks
              where collection_name='fund-gateway-contracts'
                and document_id='shengheng-api' and document_version='v1')
on conflict (set_id) do nothing;

insert into knowledge.rag_evaluation_cases
    (case_id, set_id, question, expected_keyword, expected_chunk_id, expected_locator, refusal_expected, case_order)
values
    ('11111111-1111-1111-1111-111111111101'::uuid,
     '11111111-1111-1111-1111-111111111111'::uuid,
     '授信申请金额字段是什么类型，是否必填？', 'applyAmt', 'shengheng-api:v1:117', '授信申请#117-117', false, 1),
    ('11111111-1111-1111-1111-111111111102'::uuid,
     '11111111-1111-1111-1111-111111111111'::uuid,
     '公共请求参数 requestNo 是什么？', 'requestNo', 'shengheng-api:v1:21', '1.2阅读对象及阅读建议#21-21', false, 2),
    ('11111111-1111-1111-1111-111111111103'::uuid,
     '11111111-1111-1111-1111-111111111111'::uuid,
     '不存在的火星字段有什么含义？', '火星字段', null, null, true, 3)
on conflict (case_id) do nothing;
