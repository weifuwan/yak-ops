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
import io.yak.ops.common.enums.datasource.DataSourceDbType;
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
import java.util.EnumMap;
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

    private Map<DataSourceDbType, DataSourcePlugin> plugins = Collections.emptyMap();

    @PostConstruct
    public void initialize() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DataSourcePluginBusinessImpl.class.getClassLoader();
        }

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
    public DataSourceConnection parseConnection(DataSourceDbType dbType, String connectionJson) {
        try {
            return get(dbType).parseConnection(connectionJson);
        } catch (DataSourcePluginException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS, exception.getMessage(), exception);
        }
    }

    @Override
    public DataSourceConnection mergeStoredSecrets(DataSourceDbType dbType, String submittedJson, String storedJson) {
        DataSourcePlugin plugin = get(dbType);
        String merged = secretCodec.mergeStoredSecrets(plugin.descriptor(), submittedJson, storedJson);
        return parseConnection(dbType, merged);
    }

    @Override
    public void testConnection(DataSourceDbType dbType, String connectionJson, int timeoutSeconds) {
        DataSourcePlugin plugin = get(dbType);
        requireCapability(plugin, DataSourceCapability.CONNECTION_TEST, DataSourceErrorCode.CONNECT_FAILED);
        DataSourceConnection connection = parseConnection(dbType, connectionJson);
        try {
            plugin.testConnection(connection, Math.max(1, timeoutSeconds));
        } catch (DataSourcePluginException exception) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
        }
    }

    @Override
    public DataSourceCatalog createCatalog(DataSourceDbType dbType, String connectionJson, int timeoutSeconds) {
        if (dbType == null) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, "数据源类型不能为空");
        }
        DataSourcePlugin plugin = get(dbType);
        requireCapability(plugin, DataSourceCapability.CATALOG_METADATA, DataSourceErrorCode.CATALOG_FAILED);
        try {
            DataSourceConnection connection = parseConnection(dbType, connectionJson);
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
    public String maskConnectionJson(DataSourceDbType dbType, String connectionJson) {
        return secretCodec.maskConnectionJson(get(dbType).descriptor(), connectionJson);
    }

    @Override
    public String maskSensitiveText(String value) {
        return secretCodec.maskSensitiveText(value);
    }

    @Override
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

    private DataSourcePlugin get(String dbType) {
        try {
            return get(DataSourceDbType.parse(dbType));
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        }
    }

    private DataSourcePlugin get(DataSourceDbType dbType) {
        DataSourcePlugin plugin = dbType == null ? null : plugins.get(dbType);
        if (plugin == null) {
            throw new DataSourceException(
                    DataSourceErrorCode.PLUGIN_NOT_FOUND,
                    dbType == null ? "未指定数据源类型" : "未找到插件：" + dbType.name());
        }
        return plugin;
    }

    private void validateDescriptor(DataSourcePlugin plugin) {
        if (plugin == null || plugin.dbType() == null) {
            throw new IllegalStateException("Datasource plugin and dbType must not be null");
        }
        DataSourcePluginDescriptor descriptor = plugin.descriptor();
        if (descriptor == null) {
            throw new IllegalStateException(
                    "Datasource plugin descriptor must not be null: " + plugin.getClass().getName());
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
    }

    private void requireCapability(
            DataSourcePlugin plugin, DataSourceCapability capability, DataSourceErrorCode errorCode) {
        if (!plugin.supports(capability)) {
            throw new DataSourceException(
                    errorCode, "数据源插件未声明能力 " + capability.name() + "：" + plugin.dbType().name());
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
                .pluginType(source.dbType().name())
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
