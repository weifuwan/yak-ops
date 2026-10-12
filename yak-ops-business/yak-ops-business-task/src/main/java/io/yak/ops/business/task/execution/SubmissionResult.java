package io.yak.ops.business.task.execution;

import io.yak.ops.common.enums.task.TaskInstanceStatus;

/** Identity and pinned Definition version returned after durable Task admission. */
public record SubmissionResult(
        String instanceId, String attemptId, int definitionVersion, TaskInstanceStatus status, boolean replayed) {}
