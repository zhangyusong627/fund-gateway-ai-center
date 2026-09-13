package org.practice.fundgateway.guardian.metrics;

import java.util.List;
import java.util.Optional;

/** 提供指标消息失败记录和受控重放查询能力。 */
public interface MetricEventFailureRecorder {

    /** 保存一条失败记录，不改变 Kafka 提交语义。 */
    void record(MetricEventFailure failure);

    /** 按失败 ID 查询记录。 */
    Optional<MetricEventFailure> find(String failureId);

    /** 返回当前可见的失败记录快照。 */
    List<MetricEventFailure> findAll();
}
