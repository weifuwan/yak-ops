package org.apache.flink.cdc.connectors.jdbc.mysql.factory;

import org.apache.flink.cdc.common.utils.Preconditions;
import org.apache.flink.cdc.connectors.jdbc.catalog.JdbcCatalog;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialectFactory;
import org.apache.flink.cdc.connectors.jdbc.mysql.catalog.MySqlCatalog;
import org.apache.flink.cdc.connectors.jdbc.mysql.dialect.MySqlDialect;

/** Discovers the MySQL dialect and its associated metadata catalog. */
public class MySqlJdbcDialectFactory implements JdbcDialectFactory<JdbcSinkConfig> {

    @Override
    public String identifier() {
        return "mysql";
    }

    @Override
    public JdbcDialect createDialect(JdbcSinkConfig config) {
        Preconditions.checkArgument(
                "mysql".equalsIgnoreCase(config.getDialect()),
                "The MySQL JDBC sink cannot handle dialect %s",
                config.getDialect());
        return new MySqlDialect();
    }

    @Override
    public JdbcCatalog createCatalog(JdbcSinkConfig config, JdbcDialect dialect) {
        return new MySqlCatalog(dialect, config);
    }
}
