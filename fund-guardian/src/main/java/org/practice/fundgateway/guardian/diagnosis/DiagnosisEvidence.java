package org.practice.fundgateway.guardian.diagnosis;

/** 诊断所需的三类只读证据。 */
public record DiagnosisEvidence(ContractEvidence contract, MetricsEvidence metrics, IncidentEvidence incident) {
}
