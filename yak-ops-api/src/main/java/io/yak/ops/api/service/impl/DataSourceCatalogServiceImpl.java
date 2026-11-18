package io.yak.ops.api.service.impl;

import io.yak.ops.api.service.DataSourceCatalogService;
import io.yak.ops.api.service.DataSourceService;
import io.yak.ops.api.vo.catalog.CatalogChildrenPageVO;
import io.yak.ops.api.vo.catalog.CatalogNodeType;
import io.yak.ops.api.vo.catalog.CatalogTreeNodeVO;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.datasource.api.catalog.CatalogCapabilities;
import io.yak.ops.plugin.datasource.api.catalog.CatalogFeature;
import io.yak.ops.plugin.datasource.api.catalog.CatalogIndex;
import io.yak.ops.plugin.datasource.api.catalog.CatalogNamespace;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectQuery;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPage;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Delegates catalog discovery to the registered datasource plugin. */
@Service
public class DataSourceCatalogServiceImpl implements DataSourceCatalogService {

    @Autowired
    private DataSourceService dataSourceService;

    @Override
    public List<ConnectionProperty> getConnectionProperties(DbType dbType) {
        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(dbType);
        if (processor == null) {
            throw new IllegalArgumentException("No registered datasource processor for " + dbType);
        }
        return processor.getConnectionProperties();
    }

    @Override
    public CatalogCapabilities getCapabilities(User loginUser, long projectCode, Integer datasourceId) {
        DataSourceProcessor processor = getAuthorizedProcessor(loginUser, projectCode, datasourceId);
        DataSourceCatalog catalog = processor.getCatalog();
        return catalog == null ? CatalogCapabilities.empty() : catalog.getCapabilities();
    }

    @Override
    public CatalogChildrenPageVO getChildren(User loginUser, long projectCode, Integer datasourceId,
                                             CatalogNodeType nodeType, CatalogPath path, String keyword,
                                             Integer pageNo, Integer pageSize) {
        int currentPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int currentSize = pageSize == null ? 100 : Math.max(1, Math.min(pageSize, 500));
        CatalogPath safePath = path == null ? new CatalogPath() : path;
        DataSourceProcessor processor = getAuthorizedProcessor(loginUser, projectCode, datasourceId);
        DataSourceCatalog catalog = processor.getCatalog();
        if (catalog == null) return emptyPage(currentPage, currentSize);
        CatalogCapabilities capabilities = catalog.getCapabilities();
        CatalogNodeType currentType = nodeType == null ? CatalogNodeType.DATASOURCE : nodeType;

        if (currentType == CatalogNodeType.DATASOURCE || isNamespace(currentType)) {
            NamespaceLevel nextLevel = nextNamespaceLevel(capabilities, currentType);
            if (nextLevel == null) {
                return page(groupNodes(datasourceId, safePath, capabilities), currentPage, currentSize);
            }
            List<CatalogNamespace> namespaces = withConnection(
                    loginUser, projectCode, datasourceId, processor, safePath,
                    (activeCatalog, connection) -> activeCatalog.listNamespaces(connection, safePath, nextLevel));
            List<CatalogTreeNodeVO> nodes = new ArrayList<>();
            for (CatalogNamespace namespace : namespaces) {
                nodes.add(namespaceNode(datasourceId, namespace));
            }
            return page(nodes, currentPage, currentSize);
        }

        if (isObjectGroup(currentType)) {
            CatalogObjectType objectType = objectTypeForGroup(currentType);
            CatalogPage<CatalogObject> objects = withConnection(
                    loginUser, projectCode, datasourceId, processor, safePath,
                    (activeCatalog, connection) -> activeCatalog.listObjects(connection,
                            new CatalogObjectQuery(safePath, objectType, keyword,
                                    (currentPage - 1) * currentSize, currentSize)));
            List<CatalogTreeNodeVO> nodes = new ArrayList<>();
            for (CatalogObject object : objects.getItems()) {
                nodes.add(objectNode(datasourceId, object, capabilities));
            }
            return new CatalogChildrenPageVO(nodes, objects.getTotal(), currentPage, currentSize);
        }

        if (isObject(currentType)) {
            return page(objectDetailGroups(datasourceId, safePath, capabilities), currentPage, currentSize);
        }

        if (currentType == CatalogNodeType.COLUMN_GROUP) {
            List<DataSourceCatalogColumn> columns = withConnection(
                    loginUser, projectCode, datasourceId, processor, safePath,
                    (activeCatalog, connection) -> activeCatalog.listColumns(connection, safePath));
            List<CatalogTreeNodeVO> nodes = new ArrayList<>();
            for (DataSourceCatalogColumn column : columns) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("typeName", column.getTypeName());
                metadata.put("jdbcType", column.getJdbcType());
                metadata.put("size", column.getSize());
                metadata.put("scale", column.getScale());
                metadata.put("nullable", column.getNullable());
                metadata.put("primaryKey", column.isPrimaryKey());
                metadata.put("remarks", column.getRemarks());
                nodes.add(node(datasourceId, CatalogNodeType.COLUMN, null, column.getName(),
                        false, safePath, metadata));
            }
            return page(nodes, currentPage, currentSize);
        }

        if (currentType == CatalogNodeType.INDEX_GROUP) {
            List<CatalogIndex> indexes = withConnection(
                    loginUser, projectCode, datasourceId, processor, safePath,
                    (activeCatalog, connection) -> activeCatalog.listIndexes(connection, safePath));
            List<CatalogTreeNodeVO> nodes = new ArrayList<>();
            for (CatalogIndex index : indexes) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("columns", index.getColumns());
                metadata.put("unique", index.isUnique());
                metadata.put("primary", index.isPrimary());
                nodes.add(node(datasourceId, CatalogNodeType.INDEX, null, index.getName(),
                        false, safePath, metadata));
            }
            return page(nodes, currentPage, currentSize);
        }

        return emptyPage(currentPage, currentSize);
    }

    @Override
    public List<CatalogTreeNodeVO> search(User loginUser, long projectCode, Integer datasourceId,
                                          String keyword, Integer limit) {
        if (keyword == null || keyword.trim().length() < 2) return Collections.emptyList();
        int safeLimit = limit == null ? 100 : Math.max(1, Math.min(limit, 200));
        DataSourceProcessor processor = getAuthorizedProcessor(loginUser, projectCode, datasourceId);
        if (processor.getCatalog() == null) return Collections.emptyList();
        List<CatalogObject> objects = withConnection(loginUser, projectCode, datasourceId, processor,
                new CatalogPath(), (catalog, connection) -> catalog.searchObjects(connection, keyword, safeLimit));
        List<CatalogTreeNodeVO> result = new ArrayList<>();
        CatalogCapabilities capabilities = processor.getCatalog().getCapabilities();
        for (CatalogObject object : objects) result.add(objectNode(datasourceId, object, capabilities));
        return result;
    }

    @Override
    public CatalogObject getObject(User loginUser, long projectCode, Integer datasourceId, CatalogPath path) {
        DataSourceProcessor processor = getAuthorizedProcessor(loginUser, projectCode, datasourceId);
        return withConnection(loginUser, projectCode, datasourceId, processor, path,
                (catalog, connection) -> catalog.getObject(connection, path));
    }

    @Override
    public List<DataSourceCatalogColumn> getColumns(User loginUser, long projectCode,
                                                    Integer datasourceId, CatalogPath path) {
        DataSourceProcessor processor = getAuthorizedProcessor(loginUser, projectCode, datasourceId);
        return withConnection(loginUser, projectCode, datasourceId, processor, path,
                (catalog, connection) -> catalog.listColumns(connection, path));
    }

    @Override
    public List<CatalogIndex> getIndexes(User loginUser, long projectCode,
                                         Integer datasourceId, CatalogPath path) {
        DataSourceProcessor processor = getAuthorizedProcessor(loginUser, projectCode, datasourceId);
        return withConnection(loginUser, projectCode, datasourceId, processor, path,
                (catalog, connection) -> catalog.listIndexes(connection, path));
    }

    private DataSourceProcessor getAuthorizedProcessor(User loginUser, long projectCode, Integer datasourceId) {
        DataSource source = dataSourceService.getDataSourceForCatalog(loginUser, projectCode, datasourceId);
        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(source.getType());
        if (processor == null) {
            throw new IllegalArgumentException("No registered datasource processor for " + source.getType());
        }
        return processor;
    }

    private <T> T withConnection(User loginUser, long projectCode, Integer datasourceId,
                                DataSourceProcessor processor, CatalogPath path, CatalogOperation<T> operation) {
        DataSource source = dataSourceService.getDataSourceForCatalog(loginUser, projectCode, datasourceId);
        DataSourceCatalog catalog = processor.getCatalog();
        if (catalog == null) return null;
        ConnectionParam connectionParam = processor.createConnectionParams(source.getConnectionParams());
        try (Connection connection = processor.getConnection(connectionParam, path)) {
            return operation.execute(catalog, connection);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to discover datasource catalog metadata", e);
        }
    }

    private interface CatalogOperation<T> {
        T execute(DataSourceCatalog catalog, Connection connection);
    }

    private NamespaceLevel nextNamespaceLevel(CatalogCapabilities capabilities, CatalogNodeType currentType) {
        List<NamespaceLevel> levels = capabilities.getNamespaceLevels();
        if (levels.isEmpty()) return null;
        if (currentType == CatalogNodeType.DATASOURCE) return levels.get(0);
        NamespaceLevel current = namespaceLevel(currentType);
        int index = levels.indexOf(current);
        return index >= 0 && index + 1 < levels.size() ? levels.get(index + 1) : null;
    }

    private NamespaceLevel namespaceLevel(CatalogNodeType type) {
        if (type == CatalogNodeType.CATALOG) return NamespaceLevel.CATALOG;
        if (type == CatalogNodeType.DATABASE) return NamespaceLevel.DATABASE;
        if (type == CatalogNodeType.SCHEMA) return NamespaceLevel.SCHEMA;
        return null;
    }

    private boolean isNamespace(CatalogNodeType type) {
        return type == CatalogNodeType.CATALOG || type == CatalogNodeType.DATABASE
                || type == CatalogNodeType.SCHEMA;
    }

    private boolean isObjectGroup(CatalogNodeType type) {
        return type == CatalogNodeType.TABLE_GROUP || type == CatalogNodeType.VIEW_GROUP
                || type == CatalogNodeType.MATERIALIZED_VIEW_GROUP || type == CatalogNodeType.FUNCTION_GROUP
                || type == CatalogNodeType.PROCEDURE_GROUP || type == CatalogNodeType.TRIGGER_GROUP
                || type == CatalogNodeType.SEQUENCE_GROUP;
    }

    private boolean isObject(CatalogNodeType type) {
        return type == CatalogNodeType.TABLE || type == CatalogNodeType.VIEW
                || type == CatalogNodeType.MATERIALIZED_VIEW || type == CatalogNodeType.FUNCTION
                || type == CatalogNodeType.PROCEDURE || type == CatalogNodeType.TRIGGER
                || type == CatalogNodeType.SEQUENCE;
    }

    private CatalogObjectType objectTypeForGroup(CatalogNodeType type) {
        switch (type) {
            case TABLE_GROUP: return CatalogObjectType.TABLE;
            case VIEW_GROUP: return CatalogObjectType.VIEW;
            case MATERIALIZED_VIEW_GROUP: return CatalogObjectType.MATERIALIZED_VIEW;
            case FUNCTION_GROUP: return CatalogObjectType.FUNCTION;
            case PROCEDURE_GROUP: return CatalogObjectType.PROCEDURE;
            case TRIGGER_GROUP: return CatalogObjectType.TRIGGER;
            case SEQUENCE_GROUP: return CatalogObjectType.SEQUENCE;
            default: return null;
        }
    }

    private CatalogNodeType groupType(CatalogObjectType type) {
        switch (type) {
            case TABLE: return CatalogNodeType.TABLE_GROUP;
            case VIEW: return CatalogNodeType.VIEW_GROUP;
            case MATERIALIZED_VIEW: return CatalogNodeType.MATERIALIZED_VIEW_GROUP;
            case FUNCTION: return CatalogNodeType.FUNCTION_GROUP;
            case PROCEDURE: return CatalogNodeType.PROCEDURE_GROUP;
            case TRIGGER: return CatalogNodeType.TRIGGER_GROUP;
            case SEQUENCE: return CatalogNodeType.SEQUENCE_GROUP;
            default: return null;
        }
    }

    private CatalogNodeType objectNodeType(CatalogObjectType type) {
        switch (type) {
            case TABLE: return CatalogNodeType.TABLE;
            case VIEW: return CatalogNodeType.VIEW;
            case MATERIALIZED_VIEW: return CatalogNodeType.MATERIALIZED_VIEW;
            case FUNCTION: return CatalogNodeType.FUNCTION;
            case PROCEDURE: return CatalogNodeType.PROCEDURE;
            case TRIGGER: return CatalogNodeType.TRIGGER;
            case SEQUENCE: return CatalogNodeType.SEQUENCE;
            default: return CatalogNodeType.TABLE;
        }
    }

    private List<CatalogTreeNodeVO> groupNodes(Integer datasourceId, CatalogPath path,
                                               CatalogCapabilities capabilities) {
        List<CatalogTreeNodeVO> nodes = new ArrayList<>();
        for (CatalogObjectType type : capabilities.getObjectTypes()) {
            CatalogNodeType groupType = groupType(type);
            if (groupType != null) nodes.add(node(datasourceId, groupType, type.name(),
                    groupLabel(type), true, path, Collections.<String, Object>emptyMap()));
        }
        return nodes;
    }

    private String groupLabel(CatalogObjectType type) {
        switch (type) {
            case TABLE: return "表";
            case VIEW: return "视图";
            case MATERIALIZED_VIEW: return "物化视图";
            case FUNCTION: return "函数";
            case PROCEDURE: return "存储过程";
            case TRIGGER: return "触发器";
            case SEQUENCE: return "序列";
            default: return type.name();
        }
    }

    private List<CatalogTreeNodeVO> objectDetailGroups(Integer datasourceId, CatalogPath path,
                                                       CatalogCapabilities capabilities) {
        List<CatalogTreeNodeVO> nodes = new ArrayList<>();
        if (capabilities.supportsFeature(CatalogFeature.COLUMNS)) {
            nodes.add(node(datasourceId, CatalogNodeType.COLUMN_GROUP, null, "列", true, path,
                    Collections.<String, Object>emptyMap()));
        }
        if (capabilities.supportsFeature(CatalogFeature.INDEXES)) {
            nodes.add(node(datasourceId, CatalogNodeType.INDEX_GROUP, null, "索引", true, path,
                    Collections.<String, Object>emptyMap()));
        }
        return nodes;
    }

    private CatalogTreeNodeVO namespaceNode(Integer datasourceId, CatalogNamespace namespace) {
        CatalogNodeType type = namespace.getLevel() == NamespaceLevel.CATALOG ? CatalogNodeType.CATALOG
                : namespace.getLevel() == NamespaceLevel.DATABASE ? CatalogNodeType.DATABASE : CatalogNodeType.SCHEMA;
        return node(datasourceId, type, null, namespace.getName(), namespace.isHasChildren(),
                namespace.getPath(), Collections.<String, Object>emptyMap());
    }

    private CatalogTreeNodeVO objectNode(Integer datasourceId, CatalogObject object,
                                         CatalogCapabilities capabilities) {
        CatalogNodeType type = objectNodeType(object.getType());
        boolean hasChildren = object.isHasChildren()
                && (capabilities.supportsFeature(CatalogFeature.COLUMNS)
                || capabilities.supportsFeature(CatalogFeature.INDEXES));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("comment", object.getComment());
        metadata.put("primaryKeys", object.getPrimaryKeys());
        return node(datasourceId, type, object.getType().name(), object.getName(),
                hasChildren, object.getPath(), metadata);
    }

    private CatalogTreeNodeVO node(Integer datasourceId, CatalogNodeType type, String objectType,
                                   String name, boolean hasChildren, CatalogPath path,
                                   Map<String, Object> metadata) {
        String key = type.name() + "|" + datasourceId + "|"
                + (path == null ? "" : value(path.getCatalog()) + "|" + value(path.getDatabase()) + "|"
                + value(path.getSchema()) + "|" + value(path.getObjectName()) + "|"
                + value(path.getObjectType() == null ? null : path.getObjectType().name()))
                + "|" + value(name);
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        return new CatalogTreeNodeVO(id, name, type, objectType, hasChildren, path, metadata);
    }

    private String value(String value) { return value == null ? "" : value; }

    private CatalogChildrenPageVO page(List<CatalogTreeNodeVO> nodes, int pageNo, int pageSize) {
        int start = Math.min((pageNo - 1) * pageSize, nodes.size());
        int end = Math.min(start + pageSize, nodes.size());
        return new CatalogChildrenPageVO(nodes.subList(start, end), nodes.size(), pageNo, pageSize);
    }

    private CatalogChildrenPageVO emptyPage(int pageNo, int pageSize) {
        return new CatalogChildrenPageVO(Collections.<CatalogTreeNodeVO>emptyList(), 0, pageNo, pageSize);
    }
}
