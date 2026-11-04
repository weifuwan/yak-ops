package org.apache.flink.cdc.connectors.jdbc.dialect;

import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;

/** Factory class for creating {@link JdbcSinkDialect}s. */
public interface JdbcSinkDialectFactory<T extends JdbcSinkConfig> {

    /** Gets the identifier string of JDBC sink dialect. */
    String identifier();

    /** Creates a {@link JdbcSinkDialect} using {@link JdbcSinkConfig}s. */
    JdbcSinkDialect createDialect(T option);
}
