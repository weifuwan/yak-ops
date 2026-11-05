package io.yak.ops.plugin.datasource.postgresql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.JdbcConnectionPropertyCatalog;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.util.List;

/**
 * PostgreSQL JDBC driver metadata. No database connection is established.
 */
public class PostgreSQLDataSourceCatalog {

    private static final String METADATA_URL = "jdbc:postgresql://127.0.0.1:5432/yak_ops";

    public List<ConnectionProperty> getConnectionProperties() {
        return JdbcConnectionPropertyCatalog.getConnectionProperties(
                DataSourceConstants.ORG_POSTGRESQL_DRIVER, METADATA_URL);
    }
}
