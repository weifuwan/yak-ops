package io.yak.ops.business.datasource.gateway.adapter;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor.Capability;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Datasource Plugin SPI -> Business Plugin Gateway adapter. */
@Component
@ConditionalOnDataSourceEnabled
public class SpiDataSourcePluginGateway implements DataSourcePluginGateway {

    @Resource
    private DataSourcePluginRegistry pluginRegistry;
    @Resource
    private DataSourceSecretCodec secretCodec;

    @Override
    public DataSourceDbType resolveConnectionType(String connectionJson) {
        return pluginRegistry.resolveConnectionType(connectionJson);
    }

    @Override
    public DataSourcePluginDescriptor descriptor(DataSourceDbType dbType) {
        return toBusinessDescriptor(pluginRegistry.get(dbType).descriptor());
    }

    @Override
    public ConnectionProfile normalizeConnection(DataSourceDbType dbType, String connectionJson) {
        DataSourceConnection connection = parseConnection(pluginRegistry.get(dbType), connectionJson);
        return new ConnectionProfile(connection.jdbcUrl(), connection.normalizedJson(), connection.normalizedJson());
    }

    @Override
    public String mergeStoredSecrets(DataSourceDbType dbType, String submittedJson, String storedJson) {
        DataSourcePlugin plugin = pluginRegistry.get(dbType);
        return secretCodec.mergeStoredSecrets(plugin.descriptor(), submittedJson, storedJson);
    }

    @Override
    public void testConnection(DataSourceDbType dbType, ConnectionProfile connectionProfile, int timeoutSeconds) {
        if (connectionProfile == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源连接配置不能为空");
        }
        DataSourcePlugin plugin = pluginRegistry.get(dbType);
        requireCapability(plugin, DataSourceCapability.CONNECTION_TEST, DataSourceErrorCode.CONNECT_FAILED);
        DataSourceConnection connection = parseConnection(plugin, connectionProfile.normalizedJson());
        try {
            plugin.testConnection(connection, Math.max(1, timeoutSeconds));
        } catch (DataSourceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
        }
    }

    @Override
    public String maskConnectionJson(DataSourceDbType dbType, String connectionJson) {
        DataSourcePlugin plugin = pluginRegistry.get(dbType);
        return secretCodec.maskConnectionJson(plugin.descriptor(), connectionJson);
    }

    @Override
    public String maskSensitiveText(String value) {
        return secretCodec.maskSensitiveText(value);
    }

    private DataSourceConnection parseConnection(DataSourcePlugin plugin, String connectionJson) {
        try {
            return plugin.parseConnection(connectionJson);
        } catch (DataSourcePluginException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS, exception.getMessage(), exception);
        }
    }

    private void requireCapability(
            DataSourcePlugin plugin, DataSourceCapability capability, DataSourceErrorCode errorCode) {
        if (!plugin.supports(capability)) {
            throw new DataSourceException(
                    errorCode,
                    "数据源插件未声明能力 " + capability.name() + "：" + plugin.dbType().name());
        }
    }

    private DataSourcePluginDescriptor toBusinessDescriptor(
            io.yak.ops.spi.datasource.DataSourcePluginDescriptor source) {
        if (source == null) {
            throw new DataSourceException(DataSourceErrorCode.PLUGIN_NOT_FOUND, "数据源插件描述不能为空");
        }
        return new DataSourcePluginDescriptor(
                source.dbType(),
                source.displayName(),
                source.apiVersion(),
                source.capabilities().stream()
                        .map(value -> Capability.valueOf(value.name()))
                        .collect(Collectors.toUnmodifiableSet()),
                source.connectionForm().sections().stream().map(this::toSection).toList(),
                source.connectionForm().legacyFields().stream()
                        .map(this::toField)
                        .toList(),
                source.installRequired(),
                source.installHint());
    }

    private DataSourcePluginDescriptor.FormSection toSection(
            io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormSection source) {
        return new DataSourcePluginDescriptor.FormSection(
                source.key(),
                source.title(),
                source.description(),
                source.collapsible(),
                source.defaultExpanded(),
                source.fields().stream().map(this::toField).toList());
    }

    private DataSourcePluginDescriptor.FormField toField(
            io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField source) {
        return new DataSourcePluginDescriptor.FormField(
                source.key(),
                source.label(),
                source.type().name(),
                source.placeholder(),
                source.defaultValue(),
                source.options().stream()
                        .map(value -> new DataSourcePluginDescriptor.FormOption(value.label(), value.value()))
                        .toList(),
                source.rules().stream()
                        .map(value -> new DataSourcePluginDescriptor.FormRule(
                                value.required(), value.pattern(), value.min(), value.max(), value.message()))
                        .toList(),
                source.dependsOn(),
                source.visibleWhen().stream()
                        .map(value -> new DataSourcePluginDescriptor.VisibilityCondition(
                                value.field(), value.operator().name(), value.value(), value.values()))
                        .toList(),
                source.jdbcUrlLinkage() == null
                        ? null
                        : new DataSourcePluginDescriptor.JdbcUrlLinkage(
                                source.jdbcUrlLinkage().template(),
                                source.jdbcUrlLinkage().hostField(),
                                source.jdbcUrlLinkage().portField(),
                                source.jdbcUrlLinkage().databaseField(),
                                source.jdbcUrlLinkage().preserveSuffix()));
    }
}
