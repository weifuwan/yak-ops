package io.yak.ops.core.definition;

import java.util.Objects;

/** Explicitly routes one physical source table to one sink table. */
public final class RouteDef {
    private final String sourceTable;
    private final String sinkTable;

    public RouteDef(String sourceTable, String sinkTable) {
        this.sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        this.sinkTable = Objects.requireNonNull(sinkTable, "sinkTable");
        if (sourceTable.isBlank() || sinkTable.isBlank()) {
            throw new IllegalArgumentException("Route tables must not be blank");
        }
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public String getSinkTable() {
        return sinkTable;
    }
}
