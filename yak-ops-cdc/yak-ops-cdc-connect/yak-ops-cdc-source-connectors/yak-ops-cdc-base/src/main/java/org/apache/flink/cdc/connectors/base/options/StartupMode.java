package org.apache.flink.cdc.connectors.base.options;

/**
 * Startup modes for the Flink CDC Connectors.
 *
 * @see StartupOptions
 */
public enum StartupMode {
    INITIAL,

    EARLIEST_OFFSET,

    LATEST_OFFSET,

    SPECIFIC_OFFSETS,
    COMMITTED_OFFSETS,
    TIMESTAMP,
    SNAPSHOT
}
