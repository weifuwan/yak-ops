package io.yak.ops.plugin.datasource.api.catalog;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
import java.util.List;

/**
 * Datasource metadata operations implemented by each plugin.
 * Additional database and table metadata operations can be introduced when needed.
 */
public interface DataSourceCatalog {

    List<ConnectionProperty> getConnectionProperties();

    default boolean supportsTablePaging() {
        return false;
    }

    default int countTables(Connection connection, String database, String keyword) {
        throw new UnsupportedOperationException("Table paging is not supported");
    }

    default List<String> listTables(
            Connection connection, String database, String keyword, int offset, int limit) {
        throw new UnsupportedOperationException("Table paging is not supported");
    }
}
