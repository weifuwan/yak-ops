package io.yak.ops.cdc.runtime.api.source;

/** Flink 1.20.3 source input availability contract. */
public enum InputStatus {
    MORE_AVAILABLE,
    NOTHING_AVAILABLE,
    END_OF_INPUT
}
