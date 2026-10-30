package io.yak.ops.core.definition;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.Objects;

/** A declarative source and its user-visible configuration, independent of any Connector. */
public final class SourceDef {
    private final String type;
    private final ReadonlyConfig config;

    public SourceDef(String type, ReadonlyConfig config) {
        this.type = Objects.requireNonNull(type, "type");
        if (type.isBlank()) throw new IllegalArgumentException("Source type is required");
        this.config = Objects.requireNonNull(config, "config");
    }

    public String getType() {
        return type;
    }

    public ReadonlyConfig getConfig() {
        return config;
    }
}
