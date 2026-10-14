package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.Objects;

/** One explicit table mapping projected into validated, non-secret Connector options. */
public record TableConfig(ReadonlyConfig source, ReadonlyConfig sink) {
    public TableConfig {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sink, "sink");
    }
}
