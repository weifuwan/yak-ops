package io.yak.ops.core.event;

/** Logical mutation represented by one indivisible data change event. */
public enum OperationType {
    INSERT,
    UPDATE,
    DELETE,
    REPLACE
}
