package io.yak.ops.plugin.task.api.execution;

import java.util.Objects;

/**
 * Canonical identity of a resource requiring exclusive Task execution.
 *
 * <p>Keys are constructed from resolved, Workspace-authorized resource identities.
 * They must not contain credentials or ambiguously joined table paths.
 */
public record ExecutionResource(String type, String key) {

    public ExecutionResource {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        if (type.isBlank() || type.length() > 32 || key.isBlank() || key.length() > 512) {
            throw new IllegalArgumentException("Invalid execution resource identity");
        }
    }
}
