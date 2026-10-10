package io.yak.ops.business.datasource.plugin;

import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import io.yak.ops.plugin.datasource.api.exception.DataSourcePluginException;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Datasource Service 内部的 Plugin 发现、类型解析和能力路由机制。
 *
 * <p>通过 ServiceLoader 注册当前运行时可用 Provider，并统一把 Plugin 异常映射为 DatasourceException；该组件不是 Boot 或 HTTP 的业务入口。</p>
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Component
public class DataSourcePluginRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourcePluginRegistry.class);

    @Resource
    private DataSourceSecretCodec secretCodec;

    /** 按 canonical type 和兼容 alias 建立的只读 Plugin 路由表。 */
    private Map<String, DataSourcePlugin> plugins = Collections.emptyMap();

    /** 启动时发现并校验当前 classpath 中可用的 Datasource Provider。 */
    @PostConstruct
    public void initialize() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DataSourcePluginRegistry.class.getClassLoader();
        }

        Map<String, DataSourcePlugin> discovered = new LinkedHashMap<>();
        for (DataSourcePlugin plugin : ServiceLoader.load(DataSourcePlugin.class, classLoader)) {
            validateDescriptor(plugin);
            register(discovered, plugin.descriptor().type(), plugin);
            for (String alias : plugin.descriptor().aliases()) {
                register(discovered, alias, plugin);
            }
            LOG.info(
                    "数据源插件注册完成，type={}, aliases={}, apiVersion={}, capabilities={}, implementation={}",
                    plugin.descriptor().type(),
                    plugin.descriptor().aliases(),
                    plugin.descriptor().apiVersion(),
                    plugin.descriptor().capabilities(),
                    plugin.getClass().getName());
        }
        if (discovered.isEmpty()) {
            LOG.warn("未发现可用数据源插件，数据源连接能力不可用");
        }
        plugins = Collections.unmodifiableMap(discovered);
    }

    public String resolvePluginType(String pluginType) {
        return get(pluginType).descriptor().type();
    }

    public List<String> connectionPropertyKeys(String pluginType) {
        return get(pluginType).connectionPropertyKeys();
    }

    public DataSourceConnection parseConnection(String pluginType, String connectionJson) {
        try {
            return get(pluginType).parseConnection(connectionJson);
        } catch (DataSourcePluginException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS, exception.getMessage(), exception);
        }
    }

    public String displayJdbcUrl(String pluginType, String connectionJson) {
        DataSourcePlugin plugin = get(pluginType);
        String maskedConnectionJson = secretCodec.maskConnectionJson(plugin.descriptor(), connectionJson);
        DataSourceConnection connection = parseConnection(plugin.descriptor().type(), maskedConnectionJson);
        return secretCodec.maskSensitiveText(plugin.displayJdbcUrl(connection));
    }

    public DataSourceConnection mergeStoredSecrets(String pluginType, String submittedJson, String storedJson) {
        DataSourcePlugin plugin = get(pluginType);
        String merged = secretCodec.mergeStoredSecrets(plugin.descriptor(), submittedJson, storedJson);
        return parseConnection(plugin.descriptor().type(), merged);
    }

    public void testConnection(String pluginType, String connectionJson, int timeoutSeconds) {
        DataSourcePlugin plugin = get(pluginType);
        requireCapability(plugin, DataSourceCapability.CONNECTION_TEST, DataSourceErrorCode.CONNECT_FAILED);
        DataSourceConnection connection = parseConnection(plugin.descriptor().type(), connectionJson);
        try {
            plugin.testConnection(connection, Math.max(1, timeoutSeconds));
        } catch (DataSourcePluginException exception) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
        }
    }

    public List<String> catalogDatabases(String pluginType, String connectionJson, int timeoutSeconds) {
        return catalogOperation(pluginType, connectionJson, timeoutSeconds, JdbcCatalog::listDatabases);
    }

    public List<String> catalogSchemas(String pluginType, String connectionJson, int timeoutSeconds, String database) {
        return catalogOperation(pluginType, connectionJson, timeoutSeconds, catalog -> catalog.listSchemas(database));
    }

    public List<JdbcTableInfo> catalogTables(
            String pluginType,
            String connectionJson,
            int timeoutSeconds,
            String database,
            String schema,
            String keyword,
            Integer limit) {
        return catalogOperation(
                pluginType,
                connectionJson,
                timeoutSeconds,
                catalog -> catalog.listTableInfos(database, schema, keyword, limit));
    }

    public Optional<JdbcTableInfo> catalogTable(
            String pluginType, String connectionJson, int timeoutSeconds, TableId tableId) {
        return catalogOperation(pluginType, connectionJson, timeoutSeconds, catalog -> catalog.findTable(tableId));
    }

    public List<JdbcColumnInfo> catalogColumns(
            String pluginType, String connectionJson, int timeoutSeconds, TableId tableId) {
        return catalogOperation(pluginType, connectionJson, timeoutSeconds, catalog -> catalog.getColumns(tableId));
    }

    public TableSchema catalogTableSchema(
            String pluginType, String connectionJson, int timeoutSeconds, TableId tableId) {
        return catalogOperation(pluginType, connectionJson, timeoutSeconds, catalog -> catalog.getTable(tableId));
    }

    public String maskConnectionJson(String pluginType, String connectionJson) {
        return secretCodec.maskConnectionJson(get(pluginType).descriptor(), connectionJson);
    }

    public String maskSensitiveText(String value) {
        return secretCodec.maskSensitiveText(value);
    }

    private <T> T catalogOperation(
            String pluginType, String connectionJson, int timeoutSeconds, CatalogAction<T> action) {
        DataSourcePlugin plugin = get(pluginType);
        DataSourceConnection settings = parseConnection(plugin.descriptor().type(), connectionJson);
        int safeTimeout = Math.max(1, timeoutSeconds);
        // The request-scoped Catalog is never serialized or included in a job checkpoint.
        // Each metadata operation obtains a fresh driver-isolated/SSH-aware Connection.
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(settings.jdbcUrl(), () -> {
            try {
                Connection opened = plugin.openConnection(settings, safeTimeout);
                if (opened == null) {
                    throw new SQLException("Datasource provider returned a null JDBC Connection");
                }
                return opened;
            } catch (SQLException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new SQLException("Failed to open Datasource JDBC metadata connection", exception);
            }
        })) {
            return action.apply(catalog);
        } catch (SQLException | RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_QUERY_FAILED, "读取数据源 Catalog 元数据失败", exception);
        }
    }

    @FunctionalInterface
    private interface CatalogAction<T> {
        T apply(JdbcCatalog catalog) throws SQLException;
    }

    private DataSourcePlugin get(String pluginType) {
        final String normalized;
        try {
            normalized = DataSourcePluginDescriptor.normalizeType(pluginType);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        }
        DataSourcePlugin plugin = plugins.get(normalized);
        if (plugin == null) {
            throw new DataSourceException(DataSourceErrorCode.PLUGIN_NOT_FOUND, "未找到插件：" + normalized);
        }
        return plugin;
    }

    private void register(Map<String, DataSourcePlugin> discovered, String pluginType, DataSourcePlugin plugin) {
        String normalized = DataSourcePluginDescriptor.normalizeType(pluginType);
        DataSourcePlugin existing = discovered.putIfAbsent(normalized, plugin);
        if (existing != null && existing != plugin) {
            throw new IllegalStateException("Duplicate datasource plugin type or alias "
                    + normalized
                    + ": "
                    + existing.getClass().getName()
                    + " and "
                    + plugin.getClass().getName());
        }
    }

    private void validateDescriptor(DataSourcePlugin plugin) {
        if (ObjectUtils.isNull(plugin) || StringUtils.isBlank(plugin.type())) {
            throw new IllegalStateException("Datasource plugin and type must not be null or blank");
        }
        DataSourcePluginDescriptor descriptor = plugin.descriptor();
        if (ObjectUtils.isNull(descriptor)) {
            throw new IllegalStateException("Datasource plugin descriptor must not be null: "
                    + plugin.getClass().getName());
        }
        String normalizedType = DataSourcePluginDescriptor.normalizeType(plugin.type());
        if (!descriptor.type().equals(normalizedType)) {
            throw new IllegalStateException("Datasource plugin descriptor type mismatch: plugin="
                    + normalizedType
                    + ", descriptor="
                    + descriptor.type());
        }
        if (!DataSourcePluginDescriptor.CURRENT_API_VERSION.equals(descriptor.apiVersion())) {
            throw new IllegalStateException(
                    "Unsupported datasource plugin API version " + descriptor.apiVersion() + " for " + normalizedType);
        }
    }

    private void requireCapability(
            DataSourcePlugin plugin, DataSourceCapability capability, DataSourceErrorCode errorCode) {
        if (!plugin.supports(capability)) {
            throw new DataSourceException(
                    errorCode,
                    "数据源插件未声明能力 " + capability.name() + "："
                            + plugin.descriptor().type());
        }
    }
}
