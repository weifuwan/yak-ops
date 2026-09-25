package io.yak.ops.business.datasource.plugin.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginBusiness;
import io.yak.ops.business.datasource.plugin.DataSourceSecretCodec;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormFieldVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormSectionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.JdbcUrlLinkageVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.OptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.RuleVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.VisibilityConditionVO;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 发现并管理 Datasource Plugin，并把 SPI 描述、连接和 Catalog 能力收口到统一 Business Contract。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Slf4j
@Service
@ConditionalOnDataSourceEnabled
public class DataSourcePluginBusinessImpl implements DataSourcePluginBusiness {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private DataSourceSecretCodec secretCodec;

    private Map<String, DataSourcePlugin> plugins = Collections.emptyMap();

    @PostConstruct
    public void initialize() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DataSourcePluginBusinessImpl.class.getClassLoader();
        }

        Map<String, DataSourcePlugin> discovered = new LinkedHashMap<>();
        for (DataSourcePlugin plugin : ServiceLoader.load(DataSourcePlugin.class, classLoader)) {
            validateDescriptor(plugin);
            register(discovered, plugin.type(), plugin);
            for (String alias : plugin.descriptor().aliases()) {
                register(discovered, alias, plugin);
            }
            log.info(
                    "Registered datasource plugin: type={}, aliases={}, apiVersion={}, capabilities={}, implementation={}",
                    plugin.type(),
                    plugin.descriptor().aliases(),
                    plugin.descriptor().apiVersion(),
                    plugin.descriptor().capabilities(),
                    plugin.getClass().getName());
        }
        plugins = Collections.unmodifiableMap(discovered);
    }

    @Override
    public DataSourcePluginConfigVO queryPluginConfig(String pluginType) {
        return toConfigVO(get(pluginType).descriptor());
    }

    @Override
    public boolean checkPluginAvailable(String pluginType) {
        get(pluginType);
        return true;
    }

    @Override
    public String resolvePluginType(String pluginType) {
        return get(pluginType).type();
    }

    @Override
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

    @Override
    public DataSourceConnection mergeStoredSecrets(String pluginType, String submittedJson, String storedJson) {
        DataSourcePlugin plugin = get(pluginType);
        String merged = secretCodec.mergeStoredSecrets(plugin.descriptor(), submittedJson, storedJson);
        return parseConnection(plugin.type(), merged);
    }

    @Override
    public void testConnection(String pluginType, String connectionJson, int timeoutSeconds) {
        DataSourcePlugin plugin = get(pluginType);
        requireCapability(plugin, DataSourceCapability.CONNECTION_TEST, DataSourceErrorCode.CONNECT_FAILED);
        DataSourceConnection connection = parseConnection(plugin.type(), connectionJson);
        try {
            plugin.testConnection(connection, Math.max(1, timeoutSeconds));
        } catch (DataSourcePluginException exception) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
        }
    }

    @Override
    public DataSourceCatalog createCatalog(String pluginType, String connectionJson, int timeoutSeconds) {
        DataSourcePlugin plugin = get(pluginType);
        requireCapability(plugin, DataSourceCapability.CATALOG_METADATA, DataSourceErrorCode.CATALOG_FAILED);
        try {
            DataSourceConnection connection = parseConnection(plugin.type(), connectionJson);
            return plugin.createCatalog(connection, Math.max(1, timeoutSeconds));
        } catch (DataSourceException exception) {
            throw exception;
        } catch (DataSourcePluginException exception) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, exception.getMessage(), exception);
        }
    }

    @Override
    public String maskConnectionJson(String pluginType, String connectionJson) {
        return secretCodec.maskConnectionJson(get(pluginType).descriptor(), connectionJson);
    }

    @Override
    public String maskSensitiveText(String value) {
        return secretCodec.maskSensitiveText(value);
    }

    @Override
    public String resolveConnectionType(String connectionJson) {
        try {
            JsonNode root = objectMapper.readTree(connectionJson);
            if (root == null || !root.isObject()) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接参数必须是 JSON 对象");
            }
            String value = firstText(root, "dbType", "type", "pluginType");
            if (value == null || value.trim().isEmpty()) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接参数中缺少 dbType 或 pluginType");
            }
            return get(value).type();
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
            throw new IllegalStateException(
                    "Duplicate datasource plugin type or alias "
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
            throw new IllegalStateException(
                    "Datasource plugin descriptor must not be null: " + plugin.getClass().getName());
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
                    errorCode, "数据源插件未声明能力 " + capability.name() + "：" + plugin.type());
        }
    }

    private String firstText(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = root.get(key);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private DataSourcePluginConfigVO toConfigVO(DataSourcePluginDescriptor source) {
        if (source == null) {
            return null;
        }
        return DataSourcePluginConfigVO.builder()
                .pluginType(source.type())
                .sections(source.connectionForm().sections().stream().map(this::toSectionVO).toList())
                .formFields(source.connectionForm().legacyFields().stream().map(this::toFieldVO).toList())
                .installRequired(source.installRequired())
                .installHint(source.installHint())
                .build();
    }

    private FormSectionVO toSectionVO(DataSourcePluginDescriptor.FormSection source) {
        return FormSectionVO.builder()
                .key(source.key())
                .title(source.title())
                .description(source.description())
                .collapsible(source.collapsible())
                .defaultExpanded(source.defaultExpanded())
                .fields(source.fields().stream().map(this::toFieldVO).toList())
                .build();
    }

    private FormFieldVO toFieldVO(DataSourcePluginDescriptor.FormField source) {
        return FormFieldVO.builder()
                .key(source.key())
                .label(source.label())
                .type(source.type().name())
                .placeholder(source.placeholder())
                .defaultValue(source.defaultValue())
                .options(source.options().stream()
                        .map(value -> new OptionVO(value.label(), value.value()))
                        .toList())
                .rules(source.rules().stream()
                        .map(value -> new RuleVO(
                                value.required(), value.pattern(), value.min(), value.max(), value.message()))
                        .toList())
                .dependsOn(source.dependsOn())
                .visibleWhen(source.visibleWhen().stream()
                        .map(value -> new VisibilityConditionVO(
                                value.field(), value.operator().name(), value.value(), value.values()))
                        .toList())
                .urlLinkage(toLinkageVO(source.jdbcUrlLinkage()))
                .build();
    }

    private JdbcUrlLinkageVO toLinkageVO(DataSourcePluginDescriptor.JdbcUrlLinkage source) {
        if (source == null) {
            return null;
        }
        return JdbcUrlLinkageVO.builder()
                .template(source.template())
                .hostField(source.hostField())
                .portField(source.portField())
                .databaseField(source.databaseField())
                .preserveSuffix(source.preserveSuffix())
                .build();
    }
}
