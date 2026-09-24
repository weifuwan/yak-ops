package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.management.DataSourceManager;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 负责数据库、Schema、表和字段等 Catalog 元数据读取，不承载 SQL 查询或数据预览。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogReader {

    private static final int MAX_TABLE_SEARCH_LIMIT = 200;

    @Resource
    private DataSourceManager manager;

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    @Resource
    private DataSourceProperties properties;

    @Resource
    private CatalogTableMatcher tableMatcher;

    @Resource
    private DataSourceCatalogMetadataCache metadataCache;

    @Resource
    private DataSourceCatalogDiagnostics diagnostics;

    public List<String> listDatabases(Long dataSourceId) {
        DataSourceEntity entity = manager.require(dataSourceId);
        return cached(entity, "databases", "listDatabases", () -> catalog(entity).listDatabases());
    }

    public List<String> listSchemas(Long dataSourceId, String database) {
        DataSourceEntity entity = manager.require(dataSourceId);
        return cached(entity, "schemas", "listSchemas", () -> catalog(entity).listSchemas(database), database);
    }

    public List<DataSourceTable> listTables(
            Long dataSourceId, String database, String schema, String keyword) {
        DataSourceEntity entity = manager.require(dataSourceId);
        DataSourceCatalogQuery query = new DataSourceCatalogQuery(database, schema, keyword);
        return cached(
                entity,
                "tables",
                "listTables",
                () -> catalog(entity).listTables(query),
                query.getDatabase(),
                query.getSchema(),
                query.getKeyword());
    }

    public List<DataSourceTable> searchTables(
            Long dataSourceId, String database, String schema, String keyword, Integer limit) {
        DataSourceEntity entity = manager.require(dataSourceId);
        int safeLimit = tableSearchLimit(limit);
        DataSourceCatalogQuery query = new DataSourceCatalogQuery(database, schema, keyword, safeLimit);
        return cached(
                entity,
                "table-search",
                "searchTables",
                () -> catalog(entity).listTables(query),
                query.getDatabase(),
                query.getSchema(),
                query.getKeyword(),
                query.getLimit());
    }

    public List<DataSourceColumn> listColumns(
            Long dataSourceId, String database, String schema, String table) {
        DataSourceEntity entity = manager.require(dataSourceId);
        DataSourceTablePath path = new DataSourceTablePath(database, schema, table);
        return cached(
                entity,
                "columns",
                "listColumns",
                () -> catalog(entity).listColumns(path),
                path.getDatabase(),
                path.getSchema(),
                path.getTable());
    }

    public List<DataSourceTable> listTable(Long dataSourceId) {
        return listAllTables(dataSourceId);
    }

    public List<DataSourceTable> listTableReference(Long dataSourceId, String matchMode, String keyword) {
        return tableMatcher.match(listAllTables(dataSourceId), matchMode, keyword);
    }

    public DataSourceCatalogDiagnostics.Snapshot diagnostics() {
        return diagnostics.snapshot();
    }

    private List<DataSourceTable> listAllTables(Long dataSourceId) {
        DataSourceEntity entity = manager.require(dataSourceId);
        DataSourceCatalogQuery query = new DataSourceCatalogQuery(null, null, null);
        return cached(entity, "all-tables", "listAllTables", () -> catalog(entity).listTables(query));
    }

    private DataSourceCatalog catalog(DataSourceEntity entity) {
        return pluginRegistry.createCatalog(entity, connectionTimeoutSeconds());
    }

    private <T> T cached(
            DataSourceEntity entity,
            String kind,
            String operation,
            Supplier<T> loader,
            Object... qualifiers) {
        return metadataCache.getOrLoad(
                metadataCache.key(entity, kind, qualifiers),
                metadataCacheTtlSeconds(),
                () -> diagnostics.observe(entity, operation, loader),
                diagnostics::recordCacheLookup);
    }

    private int connectionTimeoutSeconds() {
        return Math.max(1, properties.getCatalog().getConnectionTimeoutSeconds());
    }

    private int metadataCacheTtlSeconds() {
        return Math.max(0, properties.getCatalog().getMetadataCacheTtlSeconds());
    }

    private int tableSearchLimit(Integer requestedLimit) {
        int configured = Math.max(1, properties.getCatalog().getTableSearchLimit());
        int requested = requestedLimit == null ? configured : Math.max(1, requestedLimit);
        return Math.min(MAX_TABLE_SEARCH_LIMIT, requested);
    }
}
