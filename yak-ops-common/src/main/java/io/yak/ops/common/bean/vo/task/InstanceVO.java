package io.yak.ops.common.bean.vo.task;

import java.time.Instant;
import java.time.LocalDateTime;

/** Non-secret status and identity of one durable Task Instance. */
public record InstanceVO(
        String id,
        String definitionId,
        int definitionVersion,
        boolean debug,
        String taskType,
        String triggerType,
        String status,
        String runtimeEngine,
        String runtimeId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String failureCode,
        LocalDateTime createTime,
        String scheduleId,
        Long scheduleRevision,
        Instant scheduledTime,
        Integer progressPercent,
        String executionReport) {}
