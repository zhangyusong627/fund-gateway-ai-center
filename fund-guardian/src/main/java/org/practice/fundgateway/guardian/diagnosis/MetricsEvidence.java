package org.practice.fundgateway.guardian.diagnosis;

/** 观测窗口内的运行指标。 */
public record MetricsEvidence(String provider, String interfaceName, int qps, int avgLatencyMs,
                              double timeoutRate, int activeThreads, int maxThreads) {
}
