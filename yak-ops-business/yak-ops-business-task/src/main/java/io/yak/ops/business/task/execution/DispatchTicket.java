package io.yak.ops.business.task.execution;

/** Durable one-time dispatch ownership and pinned Task Definition identity. */
public record DispatchTicket(
        String workspaceId,
        String instanceId,
        String attemptId,
        String definitionId,
        int definitionVersion,
        String taskType,
        String resourceRevision,
        String dispatchToken) {}
