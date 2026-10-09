package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.source.Boundedness;
import io.yak.ops.flow.api.source.Source;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import io.yak.ops.flow.api.trace.RuntimeTraceListener;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialects;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionRuntime;
import java.util.Objects;

/**
 * YakFlow bounded JDBC 表 Source；支持整表、显式数值范围和动态数值范围分片。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class JdbcSource implements Source<JdbcSourceSplit> {

    private final JdbcSourceConfig config;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;
    private final RuntimeTraceListener traceListener;

    public JdbcSource(JdbcSourceConfig config) {
        this(config, JdbcConnectionRuntime.getInstance(), RuntimeTraceListener.noop());
    }

    public JdbcSource(JdbcSourceConfig config, RuntimeTraceListener traceListener) {
        this(config, JdbcConnectionRuntime.getInstance(), traceListener);
    }

    public JdbcSource(JdbcSourceConfig config, JdbcConnectionProvider connectionProvider) {
        this(config, connectionProvider, RuntimeTraceListener.noop());
    }

    public JdbcSource(
            JdbcSourceConfig config, JdbcConnectionProvider connectionProvider, RuntimeTraceListener traceListener) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
        this.traceListener = Objects.requireNonNull(traceListener, "traceListener must not be null");
        this.dialect = JdbcDialects.forType(config.connection().type());
    }

    @Override
    public Boundedness boundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public SourceSplitEnumerator<JdbcSourceSplit> createEnumerator() {
        return new JdbcSourceSplitEnumerator(config, connectionProvider, dialect, traceListener);
    }

    @Override
    public SourceReader<JdbcSourceSplit> createReader() {
        return new JdbcSourceReader(config, connectionProvider, dialect, traceListener);
    }
}
