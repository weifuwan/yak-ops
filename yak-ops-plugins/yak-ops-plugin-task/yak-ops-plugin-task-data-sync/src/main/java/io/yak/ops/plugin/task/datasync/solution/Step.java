package io.yak.ops.plugin.task.datasync.solution;

/** Data movement and structure-preparation steps selected by the task owner. */
public enum Step {
    SCHEMA,
    FULL,
    INCREMENTAL
}
