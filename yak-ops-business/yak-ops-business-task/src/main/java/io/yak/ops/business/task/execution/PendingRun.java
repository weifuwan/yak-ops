package io.yak.ops.business.task.execution;

/** Workspace and logical Instance identity of a durable, unclaimed Task submission. */
public record PendingRun(String workspaceId, String instanceId) {}
