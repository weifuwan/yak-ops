package io.yak.ops.core.definition;

import java.util.Objects;

/**
 * Reserved declarative transform. No transforms are executable until YakFlow has a
 * corresponding operator; the JSON parser currently rejects nonempty transform arrays.
 */
public final class TransformDef {
    private final String sourceTable;
    private final String projection;
    private final String filter;

    public TransformDef(String sourceTable, String projection, String filter) {
        this.sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        this.projection = projection;
        this.filter = filter;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public String getProjection() {
        return projection;
    }

    public String getFilter() {
        return filter;
    }
}
