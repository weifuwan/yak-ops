package io.yak.ops.business.datasource.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Discovers datasource plugins with ServiceLoader and validates their stable descriptor contract. */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
public class DataSourcePluginRegistry {

    @Resource
    private ObjectMapper objectMapper;

    private Map<DataSourceDbType, DataSourcePlugin> plugins = Collections.emptyMap();

    @PostConstruct
    public void initialize() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) classLoader = DataSourcePluginRegistry.class.getClassLoader();

        Map<DataSourceDbType, DataSourcePlugin> discovered = new EnumMap<>(DataSourceDbType.class);
        for (DataSourcePlugin plugin : ServiceLoader.load(DataSourcePlugin.class, classLoader)) {
            validateDescriptor(plugin);
            DataSourcePlugin existing = discovered.putIfAbsent(plugin.dbType(), plugin);
            if (existing != null) {
                throw new IllegalStateException("Duplicate datasource plugin for "
                        + plugin.dbType().name()
                        + ": "
                        + existing.getClass().getName()
                        + " and "
                        + plugin.getClass().getName());
            }
            log.info(
                    "Registered datasource plugin: type={}, apiVersion={}, capabilities={}, implementation={}",
                    plugin.dbType(),
                    plugin.descriptor().apiVersion(),
                    plugin.descriptor().capabilities(),
                    plugin.getClass().getName());
        }
        plugins = Collections.unmodifiableMap(discovered);
    }

    public DataSourcePlugin get(String dbType) {
        try {
            return get(DataSourceDbType.parse(dbType));
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        }
    }

    public DataSourcePlugin get(DataSourceDbType dbType) {
        DataSourcePlugin plugin = dbType == null ? null : plugins.get(dbType);
        if (plugin == null) {
            throw new DataSourceException(
                    DataSourceErrorCode.PLUGIN_NOT_FOUND, dbType == null ? "未指定数据源类型" : "未找到插件：" + dbType.name());
        }
        return plugin;
    }

    /** Parse only the routing field; the target plugin still owns connection parsing. */
    public DataSourceDbType resolveConnectionType(String connectionJson) {
        try {
            JsonNode root = objectMapper.readTree(connectionJson);
            if (root == null || !root.isObject()) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接参数必须是 JSON 对象");
            }
            String value = firstText(root, "dbType", "type", "pluginType");
            if (value == null || value.trim().isEmpty()) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接参数中缺少 dbType 或 pluginType");
            }
            return DataSourceDbType.parse(value);
        } catch (DataSourceException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "无法识别连接参数中的插件类型", exception);
        }
    }

    public Map<DataSourceDbType, DataSourcePlugin> registeredPlugins() {
        return plugins;
    }

    private void validateDescriptor(DataSourcePlugin plugin) {
        if (plugin == null || plugin.dbType() == null) {
            throw new IllegalStateException("Datasource plugin and dbType must not be null");
        }
        DataSourcePluginDescriptor descriptor = plugin.descriptor();
        if (descriptor == null) {
            throw new IllegalStateException("Datasource plugin descriptor must not be null: "
                    + plugin.getClass().getName());
        }
        if (descriptor.dbType() != plugin.dbType()) {
            throw new IllegalStateException("Datasource plugin descriptor type mismatch: plugin="
                    + plugin.dbType()
                    + ", descriptor="
                    + descriptor.dbType());
        }
        if (!DataSourcePluginDescriptor.CURRENT_API_VERSION.equals(descriptor.apiVersion())) {
            throw new IllegalStateException(
                    "Unsupported datasource plugin API version " + descriptor.apiVersion() + " for " + plugin.dbType());
        }
        if (descriptor.supports(DataSourceCapability.TRANSACTIONS)
                && !descriptor.supports(DataSourceCapability.SQL_EXECUTION)) {
            throw new IllegalStateException(
                    "Datasource plugin TRANSACTIONS requires SQL_EXECUTION: " + plugin.dbType());
        }
        if (descriptor.supports(DataSourceCapability.CATALOG_READ)
                && !descriptor.supports(DataSourceCapability.CATALOG_METADATA)) {
            throw new IllegalStateException(
                    "Datasource plugin CATALOG_READ requires CATALOG_METADATA: " + plugin.dbType());
        }
    }

    private String firstText(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = root.get(key);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }
}
