package org.practice.fundgateway.guardian.metrics;

import java.time.Instant;
import java.util.List;

/** 表示由监控适配器提交的一条合成运行时指标事件。 */
public record MetricEvent(
        String eventId,
        Instant occurredAt,
        String serviceName,
        String interfacePath,
        long requestCount,
        long errorCount,
        long timeoutCount,
        long activeThreads,
        long maxThreads,
        long gcCount,
        long gcPauseMs,
        List<Long> latencySamplesMs) {

    /** 固定延迟样本集合，避免事件进入聚合后被外部修改。 */
    public MetricEvent {
        latencySamplesMs = List.copyOf(latencySamplesMs == null ? List.of() : latencySamplesMs);
    }
}
