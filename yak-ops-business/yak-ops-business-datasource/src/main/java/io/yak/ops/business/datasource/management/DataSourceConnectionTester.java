package io.yak.ops.business.datasource.management;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 负责已保存数据源和编辑态连接参数的连通性检测，并维护最近一次连接状态。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceConnectionTester {

    @Resource
    private DataSourceManager manager;

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    @Resource
    private DataSourceProperties properties;

    public boolean testSaved(Long id) {
        DataSourceEntity entity = manager.require(id);
        try {
            pluginRegistry.testConnection(entity.getDbType(), entity.getConnectionParams(), timeoutSeconds());
            repository.updateConnectionStatus(entity.getId(), DataSourceConnStatus.CONNECTED);
            return true;
        } catch (RuntimeException exception) {
            DataSourceException mapped = connectException(exception);
            if (DataSourceErrorCode.CONNECT_FAILED.equals(mapped.getErrorCode())) {
                repository.updateConnectionStatus(entity.getId(), DataSourceConnStatus.DISCONNECTED);
            }
            throw mapped;
        }
    }

    public boolean test(DataSourceConnectionRequest request) {
        if (request == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接测试参数不能为空");
        }

        DataSourceEntity existing =
                request.dataSourceId() == null ? null : manager.require(request.dataSourceId());
        DataSourceDbType dbType;
        String connectionJson = request.connectionJson();

        if (existing != null) {
            dbType = existing.getDbType();
            if (request.requestedType() != null && request.requestedType() != dbType) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接测试的数据源类型与已保存数据源不一致");
            }
            connectionJson =
                    pluginRegistry.mergeStoredSecrets(dbType, connectionJson, existing.getConnectionParams())
                            .normalizedJson();
        } else {
            dbType = request.requestedType() == null
                    ? pluginRegistry.resolveConnectionType(connectionJson)
                    : request.requestedType();
        }

        try {
            pluginRegistry.testConnection(dbType, connectionJson, timeoutSeconds());
            return true;
        } catch (RuntimeException exception) {
            throw connectException(exception);
        }
    }

    private DataSourceException connectException(RuntimeException exception) {
        if (exception instanceof DataSourceException dataSourceException) return dataSourceException;
        return new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
    }

    private int timeoutSeconds() {
        return Math.max(1, properties.getConnectionTest().getTimeoutSeconds());
    }
}
