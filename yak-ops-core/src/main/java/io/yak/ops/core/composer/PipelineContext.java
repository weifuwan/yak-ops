package io.yak.ops.core.composer;

import java.util.Objects;

/** Trusted per-submission identity, never populated from user-authored pipeline JSON. */
public final class PipelineContext {
    private final String workspaceId;
    private final String definitionId;
    private final int definitionVersion;
    private final String instanceId;
    private final boolean debug;

    public PipelineContext(
            String workspaceId, String definitionId, int definitionVersion, String instanceId, boolean debug) {
        this.workspaceId = require(workspaceId, "workspaceId");
        this.definitionId = require(definitionId, "definitionId");
        this.instanceId = require(instanceId, "instanceId");
        if (definitionVersion < 1) throw new IllegalArgumentException("definitionVersion must be positive");
        this.definitionVersion = definitionVersion;
        this.debug = debug;
    }

    private static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String definitionId() {
        return definitionId;
    }

    public int definitionVersion() {
        return definitionVersion;
    }

    public String instanceId() {
        return instanceId;
    }

    public boolean debug() {
        return debug;
    }
}
