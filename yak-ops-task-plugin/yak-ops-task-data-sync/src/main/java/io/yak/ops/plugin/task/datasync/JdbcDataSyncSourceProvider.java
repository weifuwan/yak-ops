package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Generic bounded JDBC source connection provider for the supported relational databases. */
public final class JdbcDataSyncSourceProvider implements DataSyncSourceProvider {

    private static final Set<DbType> SUPPORTED_TYPES =
            Set.of(DbType.MYSQL, DbType.POSTGRESQL);

    @Override
    public DbType getDbType() {
        // The registry uses supports() for the other JDBC-capable database types.
        return DbType.MYSQL;
    }

    @Override
    public String getConnectorType() {
        return "jdbc";
    }

    @Override
    public boolean supports(DbType type, String connectorType) {
        return "jdbc".equalsIgnoreCase(connectorType) && SUPPORTED_TYPES.contains(type);
    }

    @Override
    public Map<String, String> connectionOptions(
            DataSourceParameters source, long taskInstanceId, int parallelism) {
        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(source.getType());
        if (processor == null) {
            throw new IllegalStateException(
                    "Datasource plugin is not installed for " + source.getType());
        }
        ConnectionParam decoded = processor.createConnectionParams(source.getConnectionParams());
        if (!(decoded instanceof BaseConnectionParam)) {
            throw new IllegalArgumentException(
                    "JDBC source requires standard JDBC connection parameters");
        }
        BaseConnectionParam params = (BaseConnectionParam) decoded;
        String driverName = processor.getDatasourceDriver();
        if (driverName == null || driverName.trim().isEmpty()) {
            driverName = params.getDriverClassName();
        }
        if (driverName == null || driverName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Datasource plugin does not provide a JDBC driver class");
        }

        Map<String, String> options = new LinkedHashMap<>();
        options.put("url", processor.getJdbcUrl(decoded));
        options.put("driver-name", driverName);
        options.put("username", params.getUser() == null ? "" : params.getUser());
        options.put("password", PasswordUtils.decodePassword(params.getPassword()));
        return options;
    }
}
