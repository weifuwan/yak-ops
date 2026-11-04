package org.apache.flink.cdc.connectors.mysql.table;

/**
 * Startup modes for the MySQL CDC Consumer.
 *
 * @see StartupOptions
 */
public enum StartupMode {
    INITIAL,

    EARLIEST_OFFSET,

    LATEST_OFFSET,

    SPECIFIC_OFFSETS,

    TIMESTAMP,
    SNAPSHOT
}
