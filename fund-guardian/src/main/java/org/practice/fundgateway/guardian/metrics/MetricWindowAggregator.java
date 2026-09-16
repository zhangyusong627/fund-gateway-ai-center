package org.practice.fundgateway.guardian.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按服务和接口聚合固定时长窗口，并计算守护规则所需核心特征。 */
public class MetricWindowAggregator {

    private final Duration windowSize;
    private final Map<WindowKey, MutableWindow> windows = new ConcurrentHashMap<>();

    /** 创建指定大小的窗口聚合器。 */
    public MetricWindowAggregator(Duration windowSize) {
        if (windowSize == null || windowSize.isZero() || windowSize.isNegative()) {
            throw new IllegalArgumentException("窗口大小必须为正数");
        }
        this.windowSize = windowSize;
    }

    /** 写入指标事件；同一事件 ID 由调用方在消费层保证幂等。 */
    public void accept(MetricEvent event) {
        if (event == null || event.occurredAt() == null || event.serviceName() == null
                || event.interfacePath() == null) {
            throw new IllegalArgumentException("指标事件缺少必要字段");
        }
        Instant start = bucketStart(event.occurredAt());
        WindowKey key = new WindowKey(event.serviceName(), event.interfacePath(), start);
        windows.computeIfAbsent(key, ignored -> new MutableWindow(start)).add(event);
    }

    /** 返回指定服务接口的窗口快照，快照不再受后续事件影响。 */
    public MetricWindowAggregate snapshot(String serviceName, String interfacePath, Instant windowStart) {
        MutableWindow window = windows.get(new WindowKey(serviceName, interfacePath, windowStart));
        return window == null ? null : window.toAggregate(serviceName, interfacePath, windowSize);
    }

    private Instant bucketStart(Instant occurredAt) {
        long seconds = windowSize.getSeconds();
        long epochSecond = occurredAt.getEpochSecond();
        return Instant.ofEpochSecond(epochSecond - Math.floorMod(epochSecond, seconds));
    }

    private record WindowKey(String serviceName, String interfacePath, Instant windowStart) {
    }

    private static final class MutableWindow {
        private final Instant start;
        private long requestCount;
        private long errorCount;
        private long timeoutCount;
        private long activeThreads;
        private long maxThreads;
        private long gcCount;
        private long gcPauseMs;
        private final List<Long> latencies = new ArrayList<>();

        private MutableWindow(Instant start) {
            this.start = start;
        }

        private synchronized void add(MetricEvent event) {
            requestCount += event.requestCount();
            errorCount += event.errorCount();
            timeoutCount += event.timeoutCount();
            activeThreads = Math.max(activeThreads, event.activeThreads());
            maxThreads = Math.max(maxThreads, event.maxThreads());
            gcCount += event.gcCount();
            gcPauseMs += event.gcPauseMs();
            latencies.addAll(event.latencySamplesMs());
        }

        private synchronized MetricWindowAggregate toAggregate(String serviceName, String interfacePath, Duration size) {
            List<Long> sorted = latencies.stream().sorted(Comparator.naturalOrder()).toList();
            long p95 = percentile(sorted, 0.95);
            long p99 = percentile(sorted, 0.99);
            double seconds = size.toMillis() / 1000.0;
            double minuteFactor = 60.0 / seconds;
            return new MetricWindowAggregate(serviceName, interfacePath, start, start.plus(size), requestCount,
                    errorCount, timeoutCount, activeThreads, maxThreads, gcCount, gcPauseMs,
                    requestCount / seconds, requestCount * minuteFactor,
                    rate(errorCount, requestCount), rate(timeoutCount, requestCount), p95, p99);
        }

        private long percentile(List<Long> sorted, double quantile) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(quantile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        private double rate(long numerator, long denominator) {
            return denominator == 0 ? 0.0 : (double) numerator / denominator;
        }
    }
}
