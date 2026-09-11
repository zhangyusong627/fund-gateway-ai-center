package org.practice.fundgateway.guardian.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 保存一次不产生真实副作用的治理模拟结果。 */
public record GovernanceSimulationRecord(
        UUID simulationId,
        String operationId,
        String actionType,
        Map<String, String> parameters,
        String result,
        String operator,
        Instant simulatedAt) {

    /** 固定治理参数并校验模拟记录。 */
    public GovernanceSimulationRecord {
        if (simulationId == null || operationId == null || operationId.isBlank()
                || actionType == null || actionType.isBlank() || operator == null
                || operator.isBlank() || simulatedAt == null) {
            throw new IllegalArgumentException("模拟治理记录缺少必要字段");
        }
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
        result = result == null ? "" : result;
    }
}
