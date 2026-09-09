package org.practice.fundgateway.guardian.diagnosis;

/** 已确认的历史故障证据。 */
public record IncidentEvidence(String provider, String interfaceName, String incidentId,
                               String status, String cause, String occurredAt) {
}
