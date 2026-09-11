create schema if not exists guardian;

create table if not exists guardian.risk_events (
    risk_fingerprint varchar(64) primary key,
    service_name varchar(128) not null,
    interface_path varchar(512) not null,
    severity varchar(32) not null,
    rule_ids text not null,
    window_start timestamptz not null,
    window_end timestamptz not null,
    occurrence_count bigint not null default 1,
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    payload_json jsonb not null,
    created_at timestamptz not null default now()
);

create table if not exists guardian.diagnostic_tasks (
    task_id uuid primary key,
    risk_fingerprint varchar(64) not null references guardian.risk_events(risk_fingerprint),
    window_start timestamptz not null,
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    unique (risk_fingerprint, window_start)
);

create table if not exists guardian.diagnosis_workflow_tasks (
    task_id uuid primary key,
    creation_key varchar(128) not null unique,
    snapshot_id varchar(128) not null,
    risk_fingerprint varchar(128) not null,
    status varchar(32) not null,
    gate_status varchar(32) not null,
    gate_reason text not null,
    report_json jsonb not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    review_deadline timestamptz
);

create index if not exists idx_diagnosis_workflow_status_updated
    on guardian.diagnosis_workflow_tasks (status, updated_at desc);

create table if not exists guardian.diagnosis_timeline (
    task_id uuid not null references guardian.diagnosis_workflow_tasks(task_id),
    event_sequence bigint not null,
    event_type varchar(64) not null,
    operator_name varchar(128) not null,
    detail text not null,
    occurred_at timestamptz not null,
    primary key (task_id, event_sequence)
);

create table if not exists guardian.diagnosis_approvals (
    task_id uuid primary key references guardian.diagnosis_workflow_tasks(task_id),
    operation_id varchar(128) not null unique,
    action varchar(32) not null,
    reviewer varchar(128) not null,
    comment text not null,
    reviewed_at timestamptz not null
);

create table if not exists guardian.governance_simulations (
    simulation_id uuid primary key,
    task_id uuid not null references guardian.diagnosis_workflow_tasks(task_id),
    operation_id varchar(128) not null,
    action_type varchar(64) not null,
    parameters_json jsonb not null,
    result varchar(64) not null,
    operator_name varchar(128) not null,
    simulated_at timestamptz not null,
    unique (task_id, operation_id)
);

create index if not exists idx_governance_simulations_task_time
    on guardian.governance_simulations (task_id, simulated_at);

create table if not exists guardian.model_call_audits (
    call_id varchar(128) primary key,
    trace_id varchar(128) not null,
    domain varchar(64) not null,
    stage varchar(64) not null,
    provider varchar(64) not null,
    model varchar(128) not null,
    prompt_version varchar(64) not null,
    input_tokens bigint not null check (input_tokens >= 0),
    output_tokens bigint not null check (output_tokens >= 0),
    total_tokens bigint not null check (total_tokens = input_tokens + output_tokens),
    latency_ms bigint not null check (latency_ms >= 0),
    status varchar(32) not null,
    retry_count integer not null check (retry_count >= 0),
    price_version varchar(64) not null,
    input_price_per_million numeric(20, 10) not null check (input_price_per_million >= 0),
    output_price_per_million numeric(20, 10) not null check (output_price_per_million >= 0),
    estimated_cost numeric(20, 10) not null check (estimated_cost >= 0),
    currency varchar(16) not null,
    raw_request text not null,
    raw_response text not null,
    called_at timestamptz not null
);

create index if not exists idx_model_call_audits_trace
    on guardian.model_call_audits (trace_id, called_at desc);

create index if not exists idx_model_call_audits_domain_stage
    on guardian.model_call_audits (domain, stage, called_at desc);
