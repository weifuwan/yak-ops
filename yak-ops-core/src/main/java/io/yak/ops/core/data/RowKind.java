package io.yak.ops.core.data;

/** Describes the database mutation represented by a row, independently of its Connector. */
public enum RowKind {
    INSERT,
    UPDATE_BEFORE,
    UPDATE_AFTER,
    DELETE
}
