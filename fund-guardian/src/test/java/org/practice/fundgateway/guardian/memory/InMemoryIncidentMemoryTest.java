package org.practice.fundgateway.guardian.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 验证长期记忆只能携带证据并按业务范围召回。 */
class InMemoryIncidentMemoryTest {

    @Test
    void shouldRecallOnlyMatchingActiveCase() {
        InMemoryIncidentMemory memory = new InMemoryIncidentMemory();
        memory.save(caseMemory(ConfirmedIncidentMemory.Status.ACTIVE, "线程池耗尽"));
        memory.save(caseMemory(ConfirmedIncidentMemory.Status.WITHDRAWN, "线程池耗尽"));

        assertEquals(1, memory.findActive("synthetic-provider", "credit-apply", "线程池").size());
    }

    @Test
    void shouldRejectCaseWithoutEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new ConfirmedIncidentMemory(
                UUID.randomUUID(), UUID.randomUUID(), "synthetic-provider", "credit-apply", "异常",
                "原因", List.of(), "approval-1", "当前接口", ConfirmedIncidentMemory.Status.ACTIVE,
                1, Instant.now()));
    }

    private ConfirmedIncidentMemory caseMemory(ConfirmedIncidentMemory.Status status, String symptom) {
        return new ConfirmedIncidentMemory(UUID.randomUUID(), UUID.randomUUID(), "synthetic-provider",
                "credit-apply", symptom, "确认的线程池容量不足", List.of("rag-001"),
                "approval-1", "当前契约版本和相同线程池配置", status, 1, Instant.now());
    }
}
