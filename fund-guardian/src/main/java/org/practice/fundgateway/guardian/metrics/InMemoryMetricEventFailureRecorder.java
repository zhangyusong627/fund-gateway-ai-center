package org.practice.fundgateway.guardian.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 保存本进程内的指标失败证据，明确不承担跨重启持久化。 */
public class InMemoryMetricEventFailureRecorder implements MetricEventFailureRecorder {

    private final Map<String, MetricEventFailure> failures = new ConcurrentHashMap<>();

    /** 记录失败证据。 */
    @Override
    public void record(MetricEventFailure failure) {
        if (failure == null || failure.failureId() == null || failure.failureId().isBlank()) {
            throw new IllegalArgumentException("失败记录及失败 ID 不能为空");
        }
        failures.put(failure.failureId(), failure);
    }

    /** 查询指定失败证据。 */
    @Override
    public Optional<MetricEventFailure> find(String failureId) {
        return Optional.ofNullable(failures.get(failureId));
    }

    /** 返回失败证据的稳定快照，避免暴露内部并发容器。 */
    @Override
    public List<MetricEventFailure> findAll() {
        return List.copyOf(new ArrayList<>(failures.values()));
    }
}
