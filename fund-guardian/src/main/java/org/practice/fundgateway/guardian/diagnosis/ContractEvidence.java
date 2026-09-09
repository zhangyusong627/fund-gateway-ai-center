package org.practice.fundgateway.guardian.diagnosis;

/**
 * 已确认的资方接口契约事实。
 */
public record ContractEvidence(String provider, String interfaceName, int qpsLimit, int timeoutMs) {
}
