-- 将在线检索 Top-K 扩展到 1~50，并区分请求上限与实际候选数。
-- 按 ADR-012 手动执行；脚本可重复执行。
ALTER TABLE knowledge.rag_query_audits
    ADD COLUMN IF NOT EXISTS actual_candidate_count integer;

UPDATE knowledge.rag_query_audits
SET actual_candidate_count = COALESCE(jsonb_array_length(candidates_json), 0)
WHERE actual_candidate_count IS NULL;

ALTER TABLE knowledge.rag_query_audits
    ALTER COLUMN actual_candidate_count SET NOT NULL;

ALTER TABLE knowledge.rag_query_audits
    DROP CONSTRAINT IF EXISTS rag_query_audits_top_k_check;

ALTER TABLE knowledge.rag_query_audits
    ADD CONSTRAINT rag_query_audits_top_k_check CHECK (top_k BETWEEN 1 AND 50);

ALTER TABLE knowledge.rag_query_audits
    DROP CONSTRAINT IF EXISTS rag_query_audits_actual_candidate_count_check;

ALTER TABLE knowledge.rag_query_audits
    ADD CONSTRAINT rag_query_audits_actual_candidate_count_check CHECK (actual_candidate_count >= 0);

ALTER TABLE knowledge.rag_query_audits
    ADD COLUMN IF NOT EXISTS retrieval_mode varchar(32);

ALTER TABLE knowledge.rag_query_audits
    ADD COLUMN IF NOT EXISTS matched_candidate_count integer;

ALTER TABLE knowledge.rag_query_audits
    ADD COLUMN IF NOT EXISTS truncated boolean;

UPDATE knowledge.rag_query_audits
SET retrieval_mode = COALESCE(retrieval_mode, 'TOP_K'),
    matched_candidate_count = COALESCE(matched_candidate_count, actual_candidate_count),
    truncated = COALESCE(truncated, false)
WHERE retrieval_mode IS NULL
   OR matched_candidate_count IS NULL
   OR truncated IS NULL;

ALTER TABLE knowledge.rag_query_audits
    ALTER COLUMN retrieval_mode SET NOT NULL;

ALTER TABLE knowledge.rag_query_audits
    ALTER COLUMN matched_candidate_count SET NOT NULL;

ALTER TABLE knowledge.rag_query_audits
    ALTER COLUMN truncated SET NOT NULL;

ALTER TABLE knowledge.rag_query_audits
    DROP CONSTRAINT IF EXISTS rag_query_audits_matched_candidate_count_check;

ALTER TABLE knowledge.rag_query_audits
    ADD CONSTRAINT rag_query_audits_matched_candidate_count_check CHECK (matched_candidate_count >= 0);

ALTER TABLE knowledge.rag_evaluation_sets
    DROP CONSTRAINT IF EXISTS rag_evaluation_sets_top_k_check;

ALTER TABLE knowledge.rag_evaluation_sets
    ADD CONSTRAINT rag_evaluation_sets_top_k_check CHECK (top_k BETWEEN 1 AND 50);
