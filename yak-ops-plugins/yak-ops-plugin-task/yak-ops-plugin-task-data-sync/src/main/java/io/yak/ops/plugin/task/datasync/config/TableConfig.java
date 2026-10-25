package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.List;
import java.util.Objects;

/** One explicit table mapping projected into validated, non-secret Connector options. */
public record TableConfig(
        ReadonlyConfig source, ReadonlyConfig sink, IncrementalConfig incremental, List<String> targetPrimaryKeys) {
    public TableConfig(ReadonlyConfig source, ReadonlyConfig sink) {
        this(source, sink, null, null);
    }

    public TableConfig(ReadonlyConfig source, ReadonlyConfig sink, IncrementalConfig incremental) {
        this(source, sink, incremental, null);
    }

    public TableConfig {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sink, "sink");
        if (targetPrimaryKeys != null) targetPrimaryKeys = List.copyOf(targetPrimaryKeys);
    }
}
