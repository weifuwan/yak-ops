package io.yak.ops.composer.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.configuration.ReadonlyConfig;
import java.util.List;
import java.util.Objects;

/** One explicit table mapping projected into validated, non-secret Connector options. */
public final class TableConfig {
    private final ReadonlyConfig source;
    private final ReadonlyConfig sink;
    private final IncrementalConfig incremental;
    private final List<String> targetPrimaryKeys;

    @JsonCreator
    public TableConfig(
            @JsonProperty("source") ReadonlyConfig source,
            @JsonProperty("sink") ReadonlyConfig sink,
            @JsonProperty("incremental") IncrementalConfig incremental,
            @JsonProperty("targetPrimaryKeys") List<String> targetPrimaryKeys) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sink, "sink");
        if (targetPrimaryKeys != null) targetPrimaryKeys = List.copyOf(targetPrimaryKeys);
        this.source = source;
        this.sink = sink;
        this.incremental = incremental;
        this.targetPrimaryKeys = targetPrimaryKeys;
    }

    @JsonProperty("source")
    public ReadonlyConfig source() {
        return source;
    }

    @JsonProperty("sink")
    public ReadonlyConfig sink() {
        return sink;
    }

    @JsonProperty("incremental")
    public IncrementalConfig incremental() {
        return incremental;
    }

    @JsonProperty("targetPrimaryKeys")
    public List<String> targetPrimaryKeys() {
        return targetPrimaryKeys;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof TableConfig other)) return false;
        return java.util.Objects.equals(source, other.source)
                && java.util.Objects.equals(sink, other.sink)
                && java.util.Objects.equals(incremental, other.incremental)
                && java.util.Objects.equals(targetPrimaryKeys, other.targetPrimaryKeys);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(source, sink, incremental, targetPrimaryKeys);
    }

    public TableConfig(ReadonlyConfig source, ReadonlyConfig sink) {
        this(source, sink, null, null);
    }

    public TableConfig(ReadonlyConfig source, ReadonlyConfig sink, IncrementalConfig incremental) {
        this(source, sink, incremental, null);
    }
}
