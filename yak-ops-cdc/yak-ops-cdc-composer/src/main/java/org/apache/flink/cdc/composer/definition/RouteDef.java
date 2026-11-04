package org.apache.flink.cdc.composer.definition;

import javax.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Definition of a router.
 *
 * <p>A router definition contains:
 *
 * <ul>
 *   <li>sourceTable: a regex pattern for matching input table IDs. Required for the definition.
 *   <li>sinkTable: a string for replacing matched table IDs as output. Required for the definition.
 *   <li>description: description for the router. Optional for the definition.
 * </ul>
 */
public class RouteDef {
    private final String sourceTable;
    private final String sinkTable;
    private final String replaceSymbol;
    @Nullable private final String description;

    public RouteDef(
            String sourceTable,
            String sinkTable,
            @Nullable String replaceSymbol,
            @Nullable String description) {
        this.sourceTable = sourceTable;
        this.sinkTable = sinkTable;
        this.replaceSymbol = replaceSymbol;
        this.description = description;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public String getSinkTable() {
        return sinkTable;
    }

    public Optional<String> getReplaceSymbol() {
        return Optional.ofNullable(replaceSymbol);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public String toString() {
        return "RouteDef{"
                + "sourceTable="
                + sourceTable
                + ", sinkTable="
                + sinkTable
                + ", replaceSymbol="
                + replaceSymbol
                + ", description='"
                + description
                + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RouteDef routeDef = (RouteDef) o;
        return Objects.equals(sourceTable, routeDef.sourceTable)
                && Objects.equals(sinkTable, routeDef.sinkTable)
                && Objects.equals(replaceSymbol, routeDef.replaceSymbol)
                && Objects.equals(description, routeDef.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceTable, sinkTable, replaceSymbol, description);
    }
}
