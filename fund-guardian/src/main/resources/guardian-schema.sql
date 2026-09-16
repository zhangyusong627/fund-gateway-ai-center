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

create table if not exists guardian.risk_cooldowns (
    risk_fingerprint varchar(64) primary key,
    next_allowed_at timestamptz not null
);

create unique index if not exists uq_guardian_active_diagnostic_risk
    on guardian.diagnostic_tasks (risk_fingerprint)
    where status = 'PENDING';

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

alter table guardian.diagnosis_workflow_tasks add column if not exists snapshot_json jsonb;

create unique index if not exists uq_diagnosis_workflow_active_risk
    on guardian.diagnosis_workflow_tasks (risk_fingerprint)
    where status in ('DIAGNOSED', 'PENDING_APPROVAL', 'APPROVED');

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

create table if not exists guardian.conversation_memory_messages (
    message_id uuid primary key,
    conversation_id varchar(128) not null,
    diagnostic_task_id uuid not null references guardian.diagnosis_workflow_tasks(task_id),
    role varchar(32) not null,
    message_type varchar(32) not null,
    content text not null,
    evidence_refs_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null,
    expires_at timestamptz,
    unique (conversation_id, message_id)
);

create index if not exists idx_conversation_memory_lookup
    on guardian.conversation_memory_messages (conversation_id, diagnostic_task_id, created_at desc);

create table if not exists guardian.conversation_memory_summaries (
    conversation_id varchar(128) not null,
    diagnostic_task_id uuid not null references guardian.diagnosis_workflow_tasks(task_id),
    summary_version integer not null,
    content text not null,
    created_at timestamptz not null,
    primary key (conversation_id, diagnostic_task_id, summary_version)
);

create table if not exists guardian.confirmed_incident_memories (
    case_id uuid primary key,
    source_task_id uuid not null references guardian.diagnosis_workflow_tasks(task_id),
    provider_id varchar(128) not null,
    interface_id varchar(256) not null,
    symptom text not null,
    confirmed_root_cause text not null,
    evidence_refs_json jsonb not null,
    approval_id varchar(128) not null unique,
    applicable_conditions text not null,
    status varchar(32) not null,
    version integer not null default 1,
    created_at timestamptz not null,
    expired_at timestamptz
);

create index if not exists idx_confirmed_incident_memory_match
    on guardian.confirmed_incident_memories (provider_id, interface_id, status);

create table if not exists guardian.agent_execution_states (
    execution_id uuid primary key,
    conversation_id varchar(128) not null,
    diagnostic_task_id uuid not null references guardian.diagnosis_workflow_tasks(task_id),
    status varchar(32) not null,
    turn integer not null check (turn >= 0),
    tool_calls integer not null check (tool_calls >= 0),
    last_error text,
    updated_at timestamptz not null,
    unique (conversation_id, diagnostic_task_id)
);
