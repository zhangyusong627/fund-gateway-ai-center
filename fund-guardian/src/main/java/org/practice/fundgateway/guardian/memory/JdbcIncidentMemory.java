package org.practice.fundgateway.guardian.memory;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;

/** 使用 PostgreSQL 保存人工确认的长期故障案例。 */
public class JdbcIncidentMemory implements IncidentMemoryPort {
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 注入数据库访问模板。 */
    public JdbcIncidentMemory(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    /** 幂等保存案例，重复案例标识不覆盖已确认内容。 */
    @Override
    public void save(ConfirmedIncidentMemory memory) {
        try {
            jdbcTemplate.update("""
                    insert into guardian.confirmed_incident_memories
                      (case_id, source_task_id, provider_id, interface_id, symptom, confirmed_root_cause,
                       evidence_refs_json, approval_id, applicable_conditions, status, version, created_at)
                    values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                    on conflict (case_id) do nothing
                    """, memory.caseId(), memory.sourceTaskId(), memory.providerId(), memory.interfaceId(),
                    memory.symptom(), memory.confirmedRootCause(), mapper.writeValueAsString(memory.evidenceRefs()),
                    memory.approvalId(), memory.applicableConditions(), memory.status().name(), memory.version(),
                    Timestamp.from(memory.createdAt()));
        } catch (Exception exception) {
            throw new IllegalStateException("长期案例记忆保存失败", exception);
        }
    }

    /** 按资方、接口和症状召回有效案例。 */
    @Override
    public List<ConfirmedIncidentMemory> findActive(String providerId, String interfaceId, String symptomKeyword) {
        return jdbcTemplate.query("""
                select case_id, source_task_id, provider_id, interface_id, symptom, confirmed_root_cause,
                       evidence_refs_json, approval_id, applicable_conditions, status, version, created_at
                  from guardian.confirmed_incident_memories
                 where provider_id=? and interface_id=? and status='ACTIVE' and lower(symptom) like lower(?)
                 order by created_at desc
                """, (rs, row) -> new ConfirmedIncidentMemory(UUID.fromString(rs.getString("case_id")),
                UUID.fromString(rs.getString("source_task_id")), rs.getString("provider_id"),
                rs.getString("interface_id"), rs.getString("symptom"), rs.getString("confirmed_root_cause"),
                readEvidenceRefs(rs.getString("evidence_refs_json")), rs.getString("approval_id"),
                rs.getString("applicable_conditions"), ConfirmedIncidentMemory.Status.valueOf(rs.getString("status")),
                rs.getInt("version"), rs.getTimestamp("created_at").toInstant()),
                providerId, interfaceId, "%" + (symptomKeyword == null ? "" : symptomKeyword) + "%");
    }

    /** 将 PostgreSQL JSON 数组恢复为证据引用列表。 */
    private List<String> readEvidenceRefs(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("长期案例证据引用解析失败", exception);
        }
    }
}
