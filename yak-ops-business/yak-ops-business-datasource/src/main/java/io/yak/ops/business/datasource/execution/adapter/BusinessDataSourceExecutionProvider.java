package io.yak.ops.business.datasource.execution.adapter;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import org.springframework.stereotype.Component;

/** Outward Task-Plugin adapter that resolves platform datasource IDs to SQL executors. */
@Component
@ConditionalOnDataSourceEnabled
public class BusinessDataSourceExecutionProvider implements DataSourceExecutionProvider {

    @Resource
    private DataSourceRepository repository;

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    @Resource
    private DataSourceProperties properties;

    @Override
    public DataSourceSqlExecutor open(String dataSourceReference) {
        long dataSourceId = parseDataSourceId(dataSourceReference);
        DataSourceDefinition definition = repository
                .findById(dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + dataSourceReference));
        DataSourcePlugin plugin = pluginRegistry.get(definition.getDbType());
        if (!plugin.supports(DataSourceCapability.SQL_EXECUTION)) {
            throw new DataSourcePluginException(
                    DataSourcePluginException.Operation.EXECUTION,
                    "当前数据源插件未声明 SQL_EXECUTION 能力：" + plugin.dbType().name());
        }
        DataSourceConnection connection = plugin.parseConnection(definition.getConnectionParams());
        return plugin.createSqlExecutor(
                connection, Math.max(1, properties.getConnectionTest().getTimeoutSeconds()));
    }

    private long parseDataSourceId(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("数据源 ID 不能为空");
        }
        try {
            long value = Long.parseLong(reference.trim());
            if (value <= 0L) throw new NumberFormatException("non-positive");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数据源 ID 非法：" + reference, exception);
        }
    }
}
