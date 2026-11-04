package org.apache.flink.cdc.debezium.table;

/** Changelog modes used to encode changes from Debezium to Flink internal structure. */
public enum DebeziumChangelogMode {
    /** Encodes changes as retract stream using all RowKinds. This is the default mode. */
    ALL("all"),
    /**
     * Encodes changes as upsert stream that describes idempotent updates on a key. Primary keys
     * must be set in tables to use this changelog mode.
     */
    UPSERT("upsert");

    private final String value;

    DebeziumChangelogMode(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
