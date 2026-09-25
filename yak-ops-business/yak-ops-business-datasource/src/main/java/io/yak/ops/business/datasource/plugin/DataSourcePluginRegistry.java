package io.yak.ops.business.datasource.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.JsonUtils;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import io.yak.ops.plugin.datasource.api.exception.DataSourcePluginException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
        plugins = Collections.unmodifiableMap(discovered);
    }

    public String resolvePluginType(String pluginType) {
        return get(pluginType).descriptor().type();
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

    public String maskConnectionJson(String pluginType, String connectionJson) {
        return secretCodec.maskConnectionJson(get(pluginType).descriptor(), connectionJson);
    }

    public String maskSensitiveText(String value) {
        return secretCodec.maskSensitiveText(value);
    }

    /**
     * 从连接 JSON 中识别并规范化目标 Plugin 类型。
     *
     * @param connectionJson 数据源连接参数
     * @return Plugin canonical type
     */
    public String resolveConnectionType(String connectionJson) {
        try {
            JsonNode root = JsonUtils.readTree(connectionJson);
            if (root == null || !root.isObject()) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接参数必须是 JSON 对象");
            }
            String value = JsonUtils.firstText(root, "dbType", "type", "pluginType");
            if (value == null || value.trim().isEmpty()) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接参数中缺少 dbType 或 pluginType");
            }
            return get(value).descriptor().type();
        } catch (DataSourceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "无法识别连接参数中的插件类型", exception);
        }
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
        if (plugin == null || plugin.type() == null || plugin.type().isBlank()) {
            throw new IllegalStateException("Datasource plugin and type must not be null or blank");
        }
        DataSourcePluginDescriptor descriptor = plugin.descriptor();
        if (descriptor == null) {
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
