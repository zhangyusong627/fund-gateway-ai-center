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
    foreign key (document_id, version) references knowledge.knowledge_documents(document_id, version)
);

create index if not exists idx_knowledge_index_tasks_status on knowledge.knowledge_index_tasks(status, updated_at);
create index if not exists idx_knowledge_chunks_collection on knowledge.knowledge_chunks(collection_name);
