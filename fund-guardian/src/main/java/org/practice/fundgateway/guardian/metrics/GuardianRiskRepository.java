package org.practice.fundgateway.guardian.metrics;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.time.Duration;

import org.springframework.jdbc.core.JdbcTemplate;

/** 将风险事件和诊断任务以幂等方式写入 PostgreSQL。 */
public class GuardianRiskRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 注入 JDBC 模板。 */
    public GuardianRiskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 创建本阶段使用的 guardian 表，重复执行不会破坏已有数据。 */
    public void ensureSchema() {
        jdbcTemplate.execute("create schema if not exists guardian");
        jdbcTemplate.execute("create table if not exists guardian.risk_events ("
                + "risk_fingerprint varchar(64) primary key, service_name varchar(128) not null,"
                + "interface_path varchar(512) not null, severity varchar(32) not null, rule_ids text not null,"
                + "window_start timestamptz not null, window_end timestamptz not null,"
                + "occurrence_count bigint not null default 1, first_seen_at timestamptz not null,"
                + "last_seen_at timestamptz not null, payload_json jsonb not null, created_at timestamptz not null default now())");
        jdbcTemplate.execute("create table if not exists guardian.diagnostic_tasks ("
                + "task_id uuid primary key, risk_fingerprint varchar(64) not null references guardian.risk_events(risk_fingerprint),"
                + "window_start timestamptz not null, status varchar(32) not null, created_at timestamptz not null default now(),"
                + "unique (risk_fingerprint, window_start))");
        jdbcTemplate.execute("create table if not exists guardian.risk_cooldowns ("
                + "risk_fingerprint varchar(64) primary key, next_allowed_at timestamptz not null)");
        jdbcTemplate.execute("create unique index if not exists uq_guardian_active_diagnostic_risk "
                + "on guardian.diagnostic_tasks (risk_fingerprint) where status='PENDING'");
    }

    /** 以数据库原子条件更新获得集群级冷却资格。 */
    public boolean tryAcquireCooldown(String fingerprint, Instant now, Duration cooldown) {
        List<Boolean> acquired = jdbcTemplate.query("insert into guardian.risk_cooldowns "
                        + "(risk_fingerprint, next_allowed_at) values (?,?) "
                        + "on conflict (risk_fingerprint) do update set next_allowed_at=? "
                        + "where guardian.risk_cooldowns.next_allowed_at <= ? returning true",
                (resultSet, rowNumber) -> resultSet.getBoolean(1), fingerprint,
                Timestamp.from(now.plus(cooldown)), Timestamp.from(now.plus(cooldown)), Timestamp.from(now));
        return acquired.stream().findFirst().orElse(false);
    }

    /** 首次写入风险事件；重复指纹只增加出现次数和最近时间。 */
    public boolean saveRiskEvent(RiskEventRecord event) {
        int inserted = jdbcTemplate.update("insert into guardian.risk_events "
                + "(risk_fingerprint, service_name, interface_path, severity, rule_ids, window_start, window_end, "
                + "occurrence_count, first_seen_at, last_seen_at, payload_json) values (?,?,?,?,?,?,?,?,?,?,?::jsonb) "
                + "on conflict (risk_fingerprint) do update set occurrence_count=guardian.risk_events.occurrence_count+1, "
                + "last_seen_at=excluded.last_seen_at, payload_json=excluded.payload_json",
                event.fingerprint(), event.serviceName(), event.interfacePath(), event.severity(),
                String.join(",", event.ruleIds()), Timestamp.from(event.windowStart()), Timestamp.from(event.windowEnd()),
                1L, Timestamp.from(event.occurredAt()), Timestamp.from(event.occurredAt()), event.payloadJson());
        return inserted > 0;
    }

    /** 为风险窗口创建诊断任务；同一活跃风险指纹只创建一次。 */
    public boolean saveDiagnosticTask(String fingerprint, Instant windowStart, String status) {
        int inserted = jdbcTemplate.update("insert into guardian.diagnostic_tasks "
                + "(task_id, risk_fingerprint, window_start, status) values (?,?,?,?) on conflict do nothing",
                UUID.randomUUID(), fingerprint, Timestamp.from(windowStart), status);
        return inserted > 0;
    }

    /** 读取一条待诊断任务及其风险事件快照。 */
    public Optional<DiagnosticTaskRecord> findPendingTask() {
        List<DiagnosticTaskRecord> records = jdbcTemplate.query("select t.task_id, t.risk_fingerprint, "
                + "t.window_start, e.payload_json from guardian.diagnostic_tasks t "
                + "join guardian.risk_events e on e.risk_fingerprint=t.risk_fingerprint "
                + "where t.status='PENDING' order by t.created_at limit 1", (rs, rowNum) ->
                new DiagnosticTaskRecord(UUID.fromString(rs.getString("task_id")),
                        rs.getString("risk_fingerprint"), rs.getTimestamp("window_start").toInstant(),
                        rs.getString("payload_json")));
        return records.stream().findFirst();
    }

    /** 更新诊断任务状态，避免同一任务被重复消费。 */
    public boolean updateDiagnosticTaskStatus(UUID taskId, String status) {
        return jdbcTemplate.update("update guardian.diagnostic_tasks set status=? where task_id=?", status, taskId) > 0;
    }

    /** 表示待落库的风险事件。 */
    public record RiskEventRecord(String fingerprint, String serviceName, String interfacePath,
                                  String severity, List<String> ruleIds, Instant windowStart,
                                  Instant windowEnd, Instant occurredAt, String payloadJson) {
    }

    /** 表示从数据库读取的待诊断任务。 */
    public record DiagnosticTaskRecord(UUID taskId, String fingerprint, Instant windowStart,
                                       String payloadJson) {
    }
}
