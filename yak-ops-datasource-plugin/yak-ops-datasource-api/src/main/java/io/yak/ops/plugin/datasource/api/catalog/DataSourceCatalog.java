package io.yak.ops.plugin.datasource.api.catalog;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;

/**
 * Datasource metadata operations implemented by each database plugin.
 * New methods are optional so existing plugins can adopt catalog features incrementally.
 */
public interface DataSourceCatalog {

    List<ConnectionProperty> getConnectionProperties();

    default CatalogCapabilities getCapabilities() {
        return CatalogCapabilities.empty();
    }

    default List<CatalogNamespace> listNamespaces(
            Connection connection, CatalogPath parentPath, NamespaceLevel level) {
        throw new UnsupportedOperationException("Namespace discovery is not supported");
    }

    default CatalogPage<CatalogObject> listObjects(Connection connection, CatalogObjectQuery query) {
        throw new UnsupportedOperationException("Object discovery is not supported");
    }

    default CatalogObject getObject(Connection connection, CatalogPath path) {
        throw new UnsupportedOperationException("Object details are not supported");
    }

    default List<CatalogIndex> listIndexes(Connection connection, CatalogPath path) {
        throw new UnsupportedOperationException("Index metadata is not supported");
    }

    default List<CatalogObject> searchObjects(Connection connection, String keyword, int limit) {
        return Collections.emptyList();
    }

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

    default List<DataSourceCatalogColumn> listColumns(Connection connection, CatalogPath path) {
        return listColumns(connection, path == null ? null : path.getDatabase(),
                path == null ? null : path.getObjectName());
    }
}
