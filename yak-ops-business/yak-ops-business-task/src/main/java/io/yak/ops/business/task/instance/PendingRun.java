package io.yak.ops.business.task.instance;

/** Workspace and logical Instance identity of a durable, unclaimed Task submission. */
public record PendingRun(String workspaceId, String instanceId) {}
