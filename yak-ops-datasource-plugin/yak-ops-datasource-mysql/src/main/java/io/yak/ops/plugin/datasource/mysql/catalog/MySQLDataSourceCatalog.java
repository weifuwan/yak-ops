package io.yak.ops.plugin.datasource.mysql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.JdbcConnectionPropertyCatalog;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.util.List;

/**
 * MySQL JDBC driver metadata. No database connection is established.
 */
public class MySQLDataSourceCatalog {

    private static final String METADATA_URL = "jdbc:mysql://127.0.0.1:3306/yak_ops";

    public List<ConnectionProperty> getConnectionProperties() {
        return JdbcConnectionPropertyCatalog.getConnectionProperties(
                DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER, METADATA_URL);
    }
}
