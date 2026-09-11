package org.practice.fundgateway.guardian.workflow;

import java.time.Instant;

/** 表示诊断任务时间线中的一条不可变事件。 */
public record DiagnosticTimelineEvent(
        long sequence,
        String eventType,
        String operator,
        String detail,
        Instant occurredAt) {

    /** 校验时间线事件的必要字段。 */
    public DiagnosticTimelineEvent {
        if (sequence <= 0 || eventType == null || eventType.isBlank()
                || operator == null || operator.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("诊断时间线事件缺少必要字段");
        }
        detail = detail == null ? "" : detail;
    }
}
