package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;

/** Public physical-submission evidence without dispatch tokens or credentials. */
public record AttemptVO(
        String id,
        int attemptNumber,
        String status,
        String runtimeEngine,
        String runtimeId,
        String logReference,
        String failureCode,
        LocalDateTime startTime,
        LocalDateTime endTime) {}
