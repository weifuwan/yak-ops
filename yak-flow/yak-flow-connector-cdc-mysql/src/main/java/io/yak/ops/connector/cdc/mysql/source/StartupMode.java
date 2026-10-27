package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Selects how a new MySQL CDC Source starts; completed checkpoints restore their saved position. */
public enum StartupMode {
    INITIAL("initial"),
    LATEST_OFFSET("latest-offset");

    private final String value;

    StartupMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean includesSnapshot() {
        return this == INITIAL;
    }

    /** Validates mode-specific source requirements before constructing a Source. */
    public void validateSchema(TableSchema schema, String chunkKeyColumn) {
        Objects.requireNonNull(schema, "schema");
        if (includesSnapshot()) {
            ChunkUtils.requireSupportedSnapshot(schema, chunkKeyColumn);
            return;
        }
        if (chunkKeyColumn != null) {
            throw new IllegalArgumentException("Snapshot Chunk Key is not supported for latest-offset startup");
        }
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("Latest-offset MySQL CDC requires a physical primary key");
        }
        for (var column : schema.columns()) {
            if (!column.dataType().isResolved()) {
                throw new IllegalArgumentException("MySQL CDC requires resolved source column types");
            }
        }
    }

    public static StartupMode from(String value) {
        for (StartupMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported MySQL CDC startup mode: " + value);
    }
}
