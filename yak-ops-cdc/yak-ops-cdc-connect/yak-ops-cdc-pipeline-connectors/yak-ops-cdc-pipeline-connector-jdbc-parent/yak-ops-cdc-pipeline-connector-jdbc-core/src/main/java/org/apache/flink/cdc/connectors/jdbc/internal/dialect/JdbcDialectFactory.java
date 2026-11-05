package org.apache.flink.cdc.connectors.jdbc.internal.dialect;

import org.apache.flink.cdc.connectors.jdbc.catalog.JdbcCatalog;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;

/** Discovers the SQL dialect and schema catalog for one JDBC target database. */
public interface JdbcDialectFactory<T extends JdbcSinkConfig> {
    String identifier();

    JdbcDialect createDialect(T config);

    JdbcCatalog createCatalog(T config, JdbcDialect dialect);
}
