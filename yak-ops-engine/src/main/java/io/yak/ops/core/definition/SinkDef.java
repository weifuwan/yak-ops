package io.yak.ops.core.definition;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.Objects;

/** A declarative sink definition. Credentials are never part of this configuration. */
public final class SinkDef {
    private final String type;
    private final String name;
    private final ReadonlyConfig config;

    public SinkDef(String type, ReadonlyConfig config) {
        this(type, null, config);
    }

    public SinkDef(String type, String name, ReadonlyConfig config) {
        this.type = Objects.requireNonNull(type, "type");
        this.name = name;
        if (type.isBlank()) throw new IllegalArgumentException("Sink type is required");
        this.config = Objects.requireNonNull(config, "config");
    }

    public java.util.Optional<String> getName() {
        return java.util.Optional.ofNullable(name);
    }

    public String getType() {
        return type;
    }

    public ReadonlyConfig getConfig() {
        return config;
    }
}
