package io.yak.ops.plugin.task.api.execution;

import java.util.Objects;

/** Non-secret engine and Job identity for one physical submission. */
public record TaskRuntimeReference(String engine, String id) {

    public TaskRuntimeReference {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(id, "id");
        if (engine.isBlank() || id.isBlank()) {
            throw new IllegalArgumentException("Runtime reference must not be blank");
        }
    }
}
