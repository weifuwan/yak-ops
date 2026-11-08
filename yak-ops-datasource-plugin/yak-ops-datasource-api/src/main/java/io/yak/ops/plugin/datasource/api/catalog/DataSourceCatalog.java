package io.yak.ops.plugin.datasource.api.catalog;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
import java.util.List;

/**
 * Datasource metadata operations implemented by each plugin.
 */
public interface DataSourceCatalog {

    List<ConnectionProperty> getConnectionProperties();

    default boolean supportsTablePaging() {
        return false;
    }

    default int countTables(Connection connection, String database, String keyword) {
        throw new UnsupportedOperationException("Table paging is not supported");
    }

    default List<DataSourceCatalogTable> listTables(
            Connection connection, String database, String keyword, int offset, int limit) {
        throw new UnsupportedOperationException("Table paging is not supported");
    }

    default List<DataSourceCatalogTable> getTables(
            Connection connection, String database, List<String> tableNames) {
        throw new UnsupportedOperationException("Table metadata is not supported");
    }

    default List<DataSourceCatalogColumn> listColumns(
            Connection connection, String database, String tableName) {
        throw new UnsupportedOperationException("Column metadata is not supported");
    }
}
