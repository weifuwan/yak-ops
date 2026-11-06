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

/** JDBC sink adapter backed by the existing Yak Ops datasource plugin. */
public final class JdbcDataSyncSinkProvider implements DataSyncSinkProvider {

    @Override
    public DbType getDbType() {
        return DbType.MYSQL;
    }

    @Override
    public String getConnectorType() {
        return "jdbc";
    }

    @Override
    public Map<String, String> connectionOptions(DataSourceParameters sink) {
        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(sink.getType());
        if (processor == null) {
            throw new IllegalStateException("MySQL datasource plugin is not installed");
        }
        ConnectionParam decoded = processor.createConnectionParams(sink.getConnectionParams());
        if (!(decoded instanceof BaseConnectionParam)) {
            throw new IllegalArgumentException("JDBC sink returned unsupported connection parameters");
        }
        BaseConnectionParam params = (BaseConnectionParam) decoded;
        Map<String, String> options = new LinkedHashMap<>();
        options.put("url", processor.getJdbcUrl(decoded));
        options.put("username", params.getUser());
        options.put("password", PasswordUtils.decodePassword(params.getPassword()));
        return options;
    }
}
