package io.yak.ops.spi.datasource;

/** Stable capabilities that a datasource plugin can explicitly advertise. */
public enum DataSourceCapability {
    CONNECTION_TEST,
    CATALOG_METADATA,
    CATALOG_READ,
    SQL_EXECUTION,
    TRANSACTIONS,
    SSH_TUNNEL
}
