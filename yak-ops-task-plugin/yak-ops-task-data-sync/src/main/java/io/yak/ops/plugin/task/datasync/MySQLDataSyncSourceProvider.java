package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** MySQL CDC connection adaptation, leaving table selection to the native PipelineDef. */
public final class MySQLDataSyncSourceProvider implements DataSyncSourceProvider {

    @Override
    public DbType getDbType() {
        return DbType.MYSQL;
    }

    @Override
    public String getConnectorType() {
        return "mysql";
    }

    @Override
    public Map<String, String> connectionOptions(DataSourceParameters source, long taskInstanceId, int parallelism) {
        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(source.getType());
        if (processor == null) {
            throw new IllegalStateException("MySQL datasource plugin is not installed");
        }
        ConnectionParam decoded = processor.createConnectionParams(source.getConnectionParams());
        if (!(decoded instanceof BaseConnectionParam)) {
            throw new IllegalArgumentException("MySQL datasource plugin returned unsupported connection parameters");
        }
        BaseConnectionParam params = (BaseConnectionParam) decoded;
        String url = processor.getJdbcUrl(params);
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("MySQL CDC source requires a jdbc:mysql:// URL");
        }
        URI uri = URI.create(url.substring("jdbc:".length()));
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("MySQL CDC source requires a single hostname");
        }
        Map<String, String> options = new LinkedHashMap<>();
        options.put("hostname", uri.getHost());
        options.put("port", String.valueOf(uri.getPort() < 0 ? 3306 : uri.getPort()));
        options.put("username", params.getUser());
        options.put("password", PasswordUtils.decodePassword(params.getPassword()));
        // Range isolates subtasks of a job. Cross-job uniqueness still requires an allocator
        // when multiple concurrently running instances may share a MySQL server.
        long base = 10000L + Math.floorMod(taskInstanceId, 1000000L) * 128L;
        options.put("server-id", parallelism == 1 ? String.valueOf(base)
                : base + "-" + (base + parallelism - 1));
        return options;
    }
}
