-- 本文件只提供待人工审核的初始化 DDL，应用启动不会自动执行。
create schema if not exists knowledge;

create table if not exists knowledge.knowledge_documents (
    document_id varchar(128) not null,
    version varchar(64) not null,
    file_path varchar(1024) not null,
    file_sha256 char(64) not null,
    status varchar(32) not null check (status in ('REGISTERED','PARSED','INDEXED','PARSE_FAILED','INDEX_FAILED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (document_id, version),
    unique (file_sha256)
);

create table if not exists knowledge.knowledge_document_elements (
    document_id varchar(128) not null,
    version varchar(64) not null,
    sequence_no integer not null,
    element_type varchar(32) not null,
    section_path varchar(512) not null,
    raw_text text not null,
    cleaned_text text not null,
    table_index integer not null default -1,
    row_index integer not null default -1,
    primary key (document_id, version, sequence_no),
    foreign key (document_id, version) references knowledge.knowledge_documents(document_id, version)
);

create table if not exists knowledge.knowledge_document_chunks (
    chunk_id varchar(256) primary key,
    document_id varchar(128) not null,
    version varchar(64) not null,
    section_path varchar(512) not null,
    content text not null,
    first_sequence integer not null,
    last_sequence integer not null,
    table_index integer not null default -1,
    row_index integer not null default -1,
    metadata jsonb not null default '{}'::jsonb,
    foreign key (document_id, version) references knowledge.knowledge_documents(document_id, version)
);

create table if not exists knowledge.rag_collections (
    collection_name varchar(128) primary key,
    description varchar(512) not null,
    embedding_provider varchar(128) not null,
    embedding_model varchar(256) not null,
    embedding_dimension integer not null,
    embedding_normalize boolean not null,
    status varchar(32) not null default 'STAGING' check (status in ('STAGING','PUBLISHED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists knowledge.knowledge_chunks (
    chunk_id varchar(256) primary key,
    collection_name varchar(128) not null references knowledge.rag_collections(collection_name),
    document varchar(512) not null,
    content text not null,
    embedding vector(512) not null,
    metadata jsonb not null,
    document_id varchar(128) not null,
    document_version varchar(64) not null,
    institution varchar(128),
    product_code varchar(128),
    operation varchar(128),
    content_type varchar(64),
    source_type varchar(64),
    block_type varchar(64),
    locator varchar(512) not null,
    embedding_provider varchar(128) not null,
    embedding_model varchar(256) not null,
    embedding_dimension integer not null,
    embedding_normalize boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists knowledge.knowledge_index_tasks (
    task_id uuid primary key,
    document_id varchar(128) not null,
    version varchar(64) not null,
    status varchar(32) not null check (status in ('CREATED','PARSING','PARSED','INDEXING','INDEXED','FAILED')),
    error_message text,
    updated_at timestamptz not null default now(),
    constraint uk_knowledge_index_tasks_document_version unique (document_id, version),
    foreign key (document_id, version) references knowledge.knowledge_documents(document_id, version)
);

create index if not exists idx_knowledge_index_tasks_status on knowledge.knowledge_index_tasks(status, updated_at);
create index if not exists idx_knowledge_chunks_collection on knowledge.knowledge_chunks(collection_name);

create table if not exists knowledge.rag_evaluation_sets (
    set_id uuid primary key,
    name varchar(256) not null,
    collection_name varchar(128) not null references knowledge.rag_collections(collection_name),
    document_id varchar(128) not null,
    document_version varchar(64) not null,
    top_k integer not null check (top_k between 1 and 10),
    created_at timestamptz not null default now(),
    unique (name, document_id, document_version)
);

create table if not exists knowledge.rag_evaluation_cases (
    case_id uuid primary key,
    set_id uuid not null references knowledge.rag_evaluation_sets(set_id),
    question text not null,
    expected_keyword varchar(256),
    expected_chunk_id varchar(256),
    expected_locator varchar(512),
    refusal_expected boolean not null default false,
    case_order integer not null,
    unique (set_id, case_order),
    check (refusal_expected or expected_chunk_id is not null or expected_locator is not null)
);

create table if not exists knowledge.rag_evaluation_runs (
    run_id uuid primary key,
    set_id uuid not null references knowledge.rag_evaluation_sets(set_id),
    status varchar(32) not null check (status in ('RUNNING','COMPLETED','FAILED')),
    recall_at_k numeric(10,6),
    mean_reciprocal_rank numeric(10,6),
    citation_accuracy numeric(10,6),
    refusal_accuracy numeric(10,6),
    error_message text,
    started_at timestamptz not null,
    completed_at timestamptz
);

create table if not exists knowledge.rag_evaluation_results (
    run_id uuid not null references knowledge.rag_evaluation_runs(run_id),
    case_id uuid not null references knowledge.rag_evaluation_cases(case_id),
    hit boolean not null,
    actual_rank integer not null,
    citation_matched boolean not null,
    retrieval_status varchar(32) not null,
    candidates_json jsonb not null,
    primary key (run_id, case_id)
);

create index if not exists idx_rag_evaluation_runs_set_time
    on knowledge.rag_evaluation_runs(set_id, started_at desc);

create table if not exists knowledge.rag_query_audits (
    query_id uuid primary key,
    collection_name varchar(128) not null,
    document_id varchar(128),
    document_version varchar(64),
    question text not null,
    keywords jsonb not null,
    top_k integer not null check (top_k between 1 and 10),
    status varchar(32) not null,
    indexed_chunks integer not null,
    duration_ms bigint not null check (duration_ms >= 0),
    missing_keywords jsonb not null,
    candidates_json jsonb not null,
    queried_at timestamptz not null
);

create index if not exists idx_rag_query_audits_time
    on knowledge.rag_query_audits(queried_at desc);
