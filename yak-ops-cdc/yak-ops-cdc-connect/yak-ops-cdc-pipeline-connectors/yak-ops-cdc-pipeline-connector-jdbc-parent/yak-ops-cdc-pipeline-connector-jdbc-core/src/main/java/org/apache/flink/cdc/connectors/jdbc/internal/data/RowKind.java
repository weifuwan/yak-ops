package org.apache.flink.cdc.connectors.jdbc.internal.data;

/** Row kind for event operator. */
public enum RowKind {
    INSERT,
    UPDATE_BEFORE,
    UPDATE_AFTER,
    DELETE,
    SCHEMA_CHANGE,
    PK_ROW;

    public boolean is(RowKind otherKind) {
        return this == otherKind;
    }
}
