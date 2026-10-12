package io.yak.ops.plugin.task.api.execution;

import java.util.Objects;

/** Immutable identity of one authorized Task execution attempt. */
public record TaskExecutionContext(
        String workspaceId, String definitionId, int definitionVersion, String instanceId, String attemptId) {

    public TaskExecutionContext {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(attemptId, "attemptId");
        if (workspaceId.isBlank() || definitionId.isBlank() || instanceId.isBlank() || attemptId.isBlank()) {
            throw new IllegalArgumentException("Task execution identities must not be blank");
        }
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("Definition version must be positive");
        }
    }
}
