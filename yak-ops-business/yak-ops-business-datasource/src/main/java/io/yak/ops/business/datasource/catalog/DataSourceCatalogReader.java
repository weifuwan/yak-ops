package io.yak.ops.business.datasource.catalog;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTablePath;
import io.yak.ops.business.datasource.domain.catalog.CatalogTableQuery;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.query.DataSourceReader;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Reads datasource catalog metadata and preview data through the typed catalog gateway. */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogReader {

    private static final int PREVIEW_LIMIT = 20;
    private static final int MAX_TABLE_SEARCH_LIMIT = 200;

    @Resource
    private DataSourceReader dataSourceReader;
    @Resource
    private DataSourceCatalogGateway catalogGateway;
    @Resource
    private DataSourceProperties properties;
    @Resource
    private CatalogReadPolicy readPolicy;
    @Resource
    private CatalogTableMatcher tableMatcher;
    @Resource
    private DataSourceCatalogMetadataCache metadataCache;
    @Resource
    private DataSourceCatalogDiagnostics diagnostics;

    public List<String> listDatabases(Long dataSourceId) {
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        return cached(
                definition,
                "databases",
                "listDatabases",
                () -> catalogGateway.listDatabases(definition, connectionTimeoutSeconds()));
    }

    public List<String> listSchemas(Long dataSourceId, String database) {
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        return cached(
                definition,
                "schemas",
                "listSchemas",
                () -> catalogGateway.listSchemas(definition, database, connectionTimeoutSeconds()),
                database);
    }

    public List<CatalogTable> listTables(Long dataSourceId, String database, String schema, String keyword) {
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        CatalogTableQuery query = new CatalogTableQuery(database, schema, keyword);
        return cached(
                definition,
                "tables",
                "listTables",
                () -> catalogGateway.listTables(definition, query, connectionTimeoutSeconds()),
                query.database(),
                query.schema(),
                query.keyword());
    }

    /**
     * Bounded table discovery for interactive selectors.
     *
     * <p>Unlike the legacy all-table endpoint, this path pushes the keyword and result limit to the
     * datasource plugin so large HIS / warehouse catalogs do not need to be materialized in the UI.
     */
    public List<CatalogTable> searchTables(
            Long dataSourceId, String database, String schema, String keyword, Integer limit) {
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        int safeLimit = tableSearchLimit(limit);
        CatalogTableQuery query = new CatalogTableQuery(database, schema, keyword, safeLimit);
        return cached(
                definition,
                "table-search",
                "searchTables",
                () -> catalogGateway.listTables(definition, query, connectionTimeoutSeconds()),
                query.database(),
                query.schema(),
                query.keyword(),
                query.limit());
    }

    public List<CatalogColumn> listColumns(Long dataSourceId, String database, String schema, String table) {
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        CatalogTablePath path = new CatalogTablePath(database, schema, table);
        return cached(
                definition,
                "columns",
                "listColumns",
                () -> catalogGateway.listColumns(definition, path, connectionTimeoutSeconds()),
                path.database(),
                path.schema(),
                path.table());
    }

    public List<CatalogTable> listTable(Long dataSourceId) {
        return listAllTables(dataSourceId);
    }

    public List<CatalogTable> listTableReference(Long dataSourceId, String matchMode, String keyword) {
        return tableMatcher.match(listAllTables(dataSourceId), matchMode, keyword);
    }

    public List<CatalogColumn> listColumn(Long dataSourceId, CatalogReadRequest request) {
        readPolicy.validateReadOnly(request);
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        return diagnostics.observe(
                definition, "describe", () -> catalogGateway.describe(definition, request, connectionTimeoutSeconds()));
    }

    public CatalogQueryResult preview(Long dataSourceId, CatalogReadRequest request) {
        readPolicy.validateReadOnly(request);
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        return diagnostics.observe(
                definition,
                "preview",
                () -> catalogGateway.preview(definition, request, PREVIEW_LIMIT, connectionTimeoutSeconds()));
    }

    public Long count(Long dataSourceId, CatalogReadRequest request) {
        readPolicy.validateReadOnly(request);
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        return diagnostics.observe(
                definition, "count", () -> catalogGateway.count(definition, request, connectionTimeoutSeconds()));
    }

    public String buildSqlTemplate(Long dataSourceId, String tablePath) {
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        return diagnostics.observe(
                definition,
                "buildSqlTemplate",
                () -> catalogGateway.buildSqlTemplate(definition, tablePath, connectionTimeoutSeconds()));
    }

    public String resolveSql(Long dataSourceId, CatalogReadRequest request) {
        readPolicy.requireSql(request);
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        return diagnostics.observe(
                definition,
                "resolveSql",
                () -> catalogGateway.resolveSql(definition, request, connectionTimeoutSeconds()));
    }

    public DataSourceCatalogDiagnostics.Snapshot diagnostics() {
        return diagnostics.snapshot();
    }

    private List<CatalogTable> listAllTables(Long dataSourceId) {
        DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
        CatalogTableQuery query = new CatalogTableQuery(null, null, null);
        return cached(
                definition,
                "all-tables",
                "listAllTables",
                () -> catalogGateway.listTables(definition, query, connectionTimeoutSeconds()));
    }

    private <T> T cached(
            DataSourceDefinition definition, String kind, String operation, Supplier<T> loader, Object... qualifiers) {
        return metadataCache.getOrLoad(
                metadataCache.key(definition, kind, qualifiers),
                metadataCacheTtlSeconds(),
                () -> diagnostics.observe(definition, operation, loader),
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
