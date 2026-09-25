package io.yak.ops.business.datasource.catalog.impl;

import io.yak.ops.business.datasource.catalog.CatalogTableMatcher;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogBusiness;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogDiagnostics;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogMetadataCache;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginBusiness;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogDiagnosticsVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * 负责编排 Datasource Plugin Catalog、缓存和诊断能力，并在 Business 边界转换为稳定 VO。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Service
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogBusinessImpl implements DataSourceCatalogBusiness {

    private static final int MAX_TABLE_SEARCH_LIMIT = 200;

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourcePluginBusiness pluginBusiness;

    @Resource
    private DataSourceProperties properties;

    @Resource
    private CatalogTableMatcher tableMatcher;

    @Resource
    private DataSourceCatalogMetadataCache metadataCache;

    @Resource
    private DataSourceCatalogDiagnostics diagnostics;

    @Override
    public DataSourceCatalogDiagnosticsVO queryDiagnostics() {
        return toDiagnosticsVO(diagnostics.snapshot());
    }

    @Override
    public List<String> queryDatabases(String dataSourceId) {
        DataSourceEntity entity = requireEntity(dataSourceId);
        return cached(entity, "databases", "listDatabases", () -> catalog(entity).listDatabases());
    }

    @Override
    public List<String> querySchemas(String dataSourceId, String database) {
        DataSourceEntity entity = requireEntity(dataSourceId);
        return cached(entity, "schemas", "listSchemas", () -> catalog(entity).listSchemas(database), database);
    }

    @Override
    public List<DataSourceCatalogTableVO> queryTables(
            String dataSourceId, String database, String schema, String keyword) {
        DataSourceEntity entity = requireEntity(dataSourceId);
        DataSourceCatalogQuery query = new DataSourceCatalogQuery(database, schema, keyword);
        return cached(
                        entity,
                        "tables",
                        "listTables",
                        () -> catalog(entity).listTables(query),
                        query.getDatabase(),
                        query.getSchema(),
                        query.getKeyword())
                .stream()
                .map(this::toTableVO)
                .toList();
    }

    @Override
    public List<DataSourceCatalogTableVO> searchTables(
            String dataSourceId, String database, String schema, String keyword, Integer limit) {
        DataSourceEntity entity = requireEntity(dataSourceId);
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
                        query.getLimit())
                .stream()
                .map(this::toTableVO)
                .toList();
    }

    @Override
    public List<DataSourceCatalogColumnVO> queryColumns(
            String dataSourceId, String database, String schema, String table) {
        DataSourceEntity entity = requireEntity(dataSourceId);
        DataSourceTablePath path = new DataSourceTablePath(database, schema, table);
        return cached(
                        entity,
                        "columns",
                        "listColumns",
                        () -> catalog(entity).listColumns(path),
                        path.getDatabase(),
                        path.getSchema(),
                        path.getTable())
                .stream()
                .map(this::toColumnVO)
                .toList();
    }

    @Override
    public List<DataSourceCatalogOptionVO> queryTableOptions(String dataSourceId) {
        return listAllTables(dataSourceId).stream().map(this::toOptionVO).toList();
    }

    @Override
    public List<DataSourceCatalogOptionVO> queryTableOptions(String dataSourceId, String matchMode, String keyword) {
        return tableMatcher.match(listAllTables(dataSourceId), matchMode, keyword).stream()
                .map(this::toOptionVO)
                .toList();
    }

    private List<DataSourceTable> listAllTables(String dataSourceId) {
        DataSourceEntity entity = requireEntity(dataSourceId);
        DataSourceCatalogQuery query = new DataSourceCatalogQuery(null, null, null);
        return cached(entity, "all-tables", "listAllTables", () -> catalog(entity).listTables(query));
    }

    private DataSourceEntity requireEntity(String id) {
        if (id == null || id.isBlank()) {
            throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
        }
        return repository.queryById(id).orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
    }

    private DataSourceCatalog catalog(DataSourceEntity entity) {
        return pluginBusiness.createCatalog(entity.getDbType(), entity.getConnectionParams(), connectionTimeoutSeconds());
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

    private DataSourceCatalogTableVO toTableVO(DataSourceTable value) {
        return new DataSourceCatalogTableVO(
                value.getDatabase(), value.getSchema(), value.getName(), value.getType(), value.getRemarks());
    }

    private DataSourceCatalogColumnVO toColumnVO(DataSourceColumn value) {
        return new DataSourceCatalogColumnVO(
                value.getName(),
                value.getTypeName(),
                value.getJdbcType(),
                value.getSize(),
                value.getScale(),
                value.isNullable(),
                value.getOrdinalPosition(),
                value.isPrimaryKey(),
                value.getRemarks());
    }

    private DataSourceCatalogOptionVO toOptionVO(DataSourceTable value) {
        String label = isBlank(value.getRemarks()) ? value.getName() : value.getRemarks();
        return new DataSourceCatalogOptionVO(value.getName(), label, value.getRemarks());
    }

    private DataSourceCatalogDiagnosticsVO toDiagnosticsVO(DataSourceCatalogDiagnostics.Snapshot snapshot) {
        var operations = snapshot.operations().stream()
                .map(operation -> new DataSourceCatalogDiagnosticsVO.OperationVO(
                        operation.operation(),
                        operation.total(),
                        operation.failures(),
                        operation.slow(),
                        operation.averageDurationMs(),
                        operation.maxDurationMs(),
                        operation.lastSlowDurationMs(),
                        operation.lastSlowTime()))
                .toList();
        return new DataSourceCatalogDiagnosticsVO(
                snapshot.cacheHits(), snapshot.cacheMisses(), snapshot.cacheHitRate(), operations);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
