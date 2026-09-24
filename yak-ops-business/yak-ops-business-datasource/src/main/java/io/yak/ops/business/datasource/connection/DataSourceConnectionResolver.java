package io.yak.ops.business.datasource.connection;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves raw connection input into a plugin-normalized business connection profile. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceConnectionResolver {

    private final DataSourcePluginGateway pluginGateway;

    public ConnectionProfile normalize(DataSourceDbType dbType, String connectionJson) {
        return pluginGateway.normalizeConnection(dbType, connectionJson);
    }

    public ConnectionProfile mergeStoredSecrets(DataSourceDefinition existing, String connectionJson) {
        String mergedConnectionJson =
                pluginGateway.mergeStoredSecrets(existing.getDbType(), connectionJson, existing.getConnectionParams());
        return normalize(existing.getDbType(), mergedConnectionJson);
    }

    public ResolvedConnection resolveTest(DataSourceConnectionRequest request, DataSourceDefinition existing) {
        if (request == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接测试参数不能为空");
        }

        String connectionJson = request.connectionJson();
        DataSourceDbType dbType;
        if (existing != null) {
            dbType = existing.getDbType();
            if (request.requestedType() != null && request.requestedType() != dbType) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接测试的数据源类型与已保存数据源不一致");
            }
            connectionJson = pluginGateway.mergeStoredSecrets(dbType, connectionJson, existing.getConnectionParams());
        } else {
            dbType = request.requestedType() != null
                    ? request.requestedType()
                    : pluginGateway.resolveConnectionType(connectionJson);
        }

        return new ResolvedConnection(dbType, normalize(dbType, connectionJson));
    }

    /** Resolved datasource type and normalized connection profile. */
    public record ResolvedConnection(DataSourceDbType dbType, ConnectionProfile profile) {}
}
