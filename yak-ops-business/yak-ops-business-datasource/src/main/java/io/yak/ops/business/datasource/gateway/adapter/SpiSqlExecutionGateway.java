package io.yak.ops.business.datasource.gateway.adapter;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.gateway.SqlExecutionGateway;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.util.List;
import org.springframework.stereotype.Component;

/** Datasource SQL execution SPI -> Business SQL execution Gateway Adapter. */
@Component
@ConditionalOnDataSourceEnabled
public class SpiSqlExecutionGateway implements SqlExecutionGateway {

    @Resource
    private DataSourceExecutionProvider executionProvider;

    @Override
    public Session open(String dataSourceId) {
        return new SessionAdapter(executionProvider.open(dataSourceId));
    }

    private static final class SessionAdapter implements Session {

        private final DataSourceSqlExecutor delegate;

        private SessionAdapter(DataSourceSqlExecutor delegate) {
            if (delegate == null) throw new IllegalStateException("Datasource SQL executor must not be null");
            this.delegate = delegate;
        }

        @Override
        public Result execute(Command command) {
            DataSourceSqlResult result = delegate.execute(new DataSourceSqlRequest(
                    command.sql(), command.maxRows(), command.timeoutSeconds(), command.parameters()));
            return new Result(
                    result.resultSet(),
                    mapColumns(result.columns()),
                    result.rows(),
                    result.affectedRows(),
                    result.truncated());
        }

        @Override
        public boolean supportsTransactions() {
            return delegate.supportsTransactions();
        }

        @Override
        public void beginTransaction() {
            delegate.beginTransaction();
        }

        @Override
        public void commitTransaction() {
            delegate.commitTransaction();
        }

        @Override
        public void rollbackTransaction() {
            delegate.rollbackTransaction();
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public void close() {
            delegate.close();
        }

        private static List<Column> mapColumns(List<DataSourceSqlColumn> columns) {
            if (columns == null || columns.isEmpty()) return List.of();
            return columns.stream()
                    .map(column -> new Column(
                            column.name(), column.label(), column.typeName(), column.jdbcType(), column.nullable()))
                    .toList();
        }
    }
}
