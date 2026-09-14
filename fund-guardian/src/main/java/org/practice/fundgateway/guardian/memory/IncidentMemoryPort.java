package org.practice.fundgateway.guardian.memory;

import java.util.List;

/** 定义长期案例记忆的保存和受限召回端口。 */
public interface IncidentMemoryPort {

    /** 保存已通过人工确认的案例。 */
    void save(ConfirmedIncidentMemory memory);

    /** 按资方、接口和症状关键词召回有效案例。 */
    List<ConfirmedIncidentMemory> findActive(String providerId, String interfaceId, String symptomKeyword);
}
