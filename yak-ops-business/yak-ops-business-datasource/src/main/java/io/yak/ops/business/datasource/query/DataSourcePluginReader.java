package io.yak.ops.business.datasource.query;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads datasource plugin capabilities through the business plugin gateway. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourcePluginReader {

    private final DataSourcePluginGateway pluginGateway;

    public DataSourcePluginDescriptor get(String pluginType) {
        return pluginGateway.descriptor(parseDbType(pluginType));
    }

    public String maskSensitiveText(String value) {
        return pluginGateway.maskSensitiveText(value);
    }

    public String maskConnectionJson(DataSourceDbType dbType, String value) {
        return pluginGateway.maskConnectionJson(dbType, value);
    }

    /** Legacy install endpoint is an availability check because plugins are discovered at startup. */
    public boolean install(String pluginType) {
        pluginGateway.descriptor(parseDbType(pluginType));
        return true;
    }

    private DataSourceDbType parseDbType(String value) {
        try {
            return DataSourceDbType.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        }
    }
}
