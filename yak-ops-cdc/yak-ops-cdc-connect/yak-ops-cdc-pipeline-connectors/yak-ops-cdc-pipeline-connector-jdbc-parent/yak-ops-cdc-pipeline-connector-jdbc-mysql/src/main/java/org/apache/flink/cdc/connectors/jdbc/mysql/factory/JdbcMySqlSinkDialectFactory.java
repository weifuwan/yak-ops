package org.apache.flink.cdc.connectors.jdbc.mysql.factory;

import org.apache.flink.cdc.common.utils.Preconditions;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcSinkDialect;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcSinkDialectFactory;
import org.apache.flink.cdc.connectors.jdbc.mysql.dialect.MySqlJdbcSinkDialect;

/** A {@link JdbcSinkDialectFactory} for MySQL variants. */
public class JdbcMySqlSinkDialectFactory implements JdbcSinkDialectFactory<JdbcSinkConfig> {
    public static final String IDENTIFIER = "mysql";

    @Override
    public String identifier() {
        return IDENTIFIER;
    }

    @Override
    public JdbcSinkDialect createDialect(JdbcSinkConfig config) {
        Preconditions.checkArgument(
                IDENTIFIER.equalsIgnoreCase(config.getDialect()),
                "JDBC sink with `%s` dialect doesn't work with specified dialect %s (inferred from connection URL: %s)",
                IDENTIFIER,
                config.getDialect(),
                config.getConnUrl());
        return new MySqlJdbcSinkDialect(IDENTIFIER, config);
    }
}
