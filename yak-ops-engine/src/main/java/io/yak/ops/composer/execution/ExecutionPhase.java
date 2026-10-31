package io.yak.ops.composer.execution;

/** Data movement and structure-preparation steps selected by the task owner. */
public enum ExecutionPhase {
    SCHEMA,
    FULL,
    INCREMENTAL
}
