package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;

/** Non-secret status and identity of one durable Task Instance. */
public record InstanceVO(
        String id,
        String definitionId,
        int definitionVersion,
        String taskType,
        String triggerType,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String failureCode,
        LocalDateTime createTime) {}
