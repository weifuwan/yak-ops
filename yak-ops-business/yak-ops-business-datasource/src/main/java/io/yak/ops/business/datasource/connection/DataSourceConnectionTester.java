package io.yak.ops.business.datasource.connection;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.connection.DataSourceConnectionResolver.ResolvedConnection;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import org.springframework.stereotype.Component;

/** Executes saved and unsaved datasource connectivity probes. */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceConnectionTester {

    @Resource
    private DataSourceReader reader;
    @Resource
    private DataSourceRepository repository;
    @Resource
    private DataSourceConnectionResolver resolver;
    @Resource
    private DataSourcePluginGateway pluginGateway;
    @Resource
    private DataSourceProperties properties;

    public boolean testSaved(Long id) {
        DataSourceDefinition definition = reader.require(id);
        try {
            pluginGateway.testConnection(
                    definition.getDbType(), definition.connectionProfile(), connectionTimeoutSeconds());
            definition.markConnected();
            repository.updateConnectionStatus(definition.getId(), definition.getConnStatus());
            return true;
        } catch (RuntimeException exception) {
            DataSourceException mapped = connectException(exception);
            if (DataSourceErrorCode.CONNECT_FAILED.equals(mapped.getErrorCode())) {
                definition.markDisconnected();
                repository.updateConnectionStatus(definition.getId(), definition.getConnStatus());
            }
            throw mapped;
        }
    }

    public boolean test(DataSourceConnectionRequest request) {
        DataSourceDefinition existing =
                request != null && request.dataSourceId() != null ? reader.require(request.dataSourceId()) : null;
        ResolvedConnection resolved = resolver.resolveTest(request, existing);
        try {
            pluginGateway.testConnection(resolved.dbType(), resolved.profile(), connectionTimeoutSeconds());
            return true;
        } catch (RuntimeException exception) {
            throw connectException(exception);
        }
    }

    private DataSourceException connectException(RuntimeException exception) {
        if (exception instanceof DataSourceException dataSourceException) {
            return dataSourceException;
        }
        return new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
    }

    private int connectionTimeoutSeconds() {
        return Math.max(1, properties.getConnectionTest().getTimeoutSeconds());
    }
}
