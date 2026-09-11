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
