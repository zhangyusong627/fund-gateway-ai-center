package org.practice.fundgateway.guardian.memory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 用于学习验收的长期案例记忆内存适配器。 */
public class InMemoryIncidentMemory implements IncidentMemoryPort {

    private final CopyOnWriteArrayList<ConfirmedIncidentMemory> memories = new CopyOnWriteArrayList<>();

    /** 保存案例，重复 caseId 不重复写入。 */
    @Override
    public void save(ConfirmedIncidentMemory memory) {
        memories.removeIf(existing -> existing.caseId().equals(memory.caseId()));
        memories.add(memory);
    }

    /** 只返回有效且匹配资方、接口和症状的案例。 */
    @Override
    public List<ConfirmedIncidentMemory> findActive(String providerId, String interfaceId, String symptomKeyword) {
        String keyword = symptomKeyword == null ? "" : symptomKeyword.toLowerCase();
        return memories.stream().filter(memory -> memory.status() == ConfirmedIncidentMemory.Status.ACTIVE
                && memory.providerId().equals(providerId) && memory.interfaceId().equals(interfaceId)
                && memory.symptom().toLowerCase().contains(keyword)).toList();
    }
}
