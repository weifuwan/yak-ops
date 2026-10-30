package io.yak.ops.core.definition;

import java.util.Objects;

/** Explicitly routes one physical source table to one sink table. */
public final class RouteDef {
    private final String sourceTable;
    private final String sinkTable;
    private final String replaceSymbol;
    private final String description;

    public RouteDef(String sourceTable, String sinkTable) {
        this(sourceTable, sinkTable, null, null);
    }

    public RouteDef(String sourceTable, String sinkTable, String replaceSymbol, String description) {
        this.sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        this.sinkTable = Objects.requireNonNull(sinkTable, "sinkTable");
        this.replaceSymbol = replaceSymbol;
        this.description = description;
        if (sourceTable.isBlank() || sinkTable.isBlank()) {
            throw new IllegalArgumentException("Route tables must not be blank");
        }
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public java.util.Optional<String> getReplaceSymbol() {
        return java.util.Optional.ofNullable(replaceSymbol);
    }

    public java.util.Optional<String> getDescription() {
        return java.util.Optional.ofNullable(description);
    }

    public String getSinkTable() {
        return sinkTable;
    }
}
