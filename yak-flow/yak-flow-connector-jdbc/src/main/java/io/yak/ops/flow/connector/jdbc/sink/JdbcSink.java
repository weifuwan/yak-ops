package io.yak.ops.flow.connector.jdbc.sink;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.Sink;
import io.yak.ops.flow.api.sink.SinkWriter;
import io.yak.ops.flow.api.trace.RuntimeTraceListener;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialects;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionRuntime;
import java.util.Objects;

/**
 * YakFlow JDBC batch Sink；目标表必须已经存在。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class JdbcSink implements Sink {

    private final JdbcSinkConfig config;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;
    private final YakTableSchema writeSchema;
    private final RuntimeTraceListener traceListener;

    public JdbcSink(JdbcSinkConfig config) {
        this(config, null, JdbcConnectionRuntime.getInstance(), RuntimeTraceListener.noop());
    }

    public JdbcSink(JdbcSinkConfig config, RuntimeTraceListener traceListener) {
        this(config, null, JdbcConnectionRuntime.getInstance(), traceListener);
    }

    /**
     * 使用显式目标写入 Schema。适用于来源与目标列名大小写或物理顺序不同、但按字段映射保持同一行值顺序的场景。
     */
    public JdbcSink(JdbcSinkConfig config, YakTableSchema writeSchema) {
        this(config, writeSchema, JdbcConnectionRuntime.getInstance(), RuntimeTraceListener.noop());
    }

    public JdbcSink(JdbcSinkConfig config, YakTableSchema writeSchema, RuntimeTraceListener traceListener) {
        this(config, writeSchema, JdbcConnectionRuntime.getInstance(), traceListener);
    }

    public JdbcSink(JdbcSinkConfig config, JdbcConnectionProvider connectionProvider) {
        this(config, null, connectionProvider, RuntimeTraceListener.noop());
    }

    public JdbcSink(JdbcSinkConfig config, YakTableSchema writeSchema, JdbcConnectionProvider connectionProvider) {
        this(config, writeSchema, connectionProvider, RuntimeTraceListener.noop());
    }

    public JdbcSink(
            JdbcSinkConfig config, JdbcConnectionProvider connectionProvider, RuntimeTraceListener traceListener) {
        this(config, null, connectionProvider, traceListener);
    }

    JdbcSink(
            JdbcSinkConfig config,
            YakTableSchema writeSchema,
            JdbcConnectionProvider connectionProvider,
            RuntimeTraceListener traceListener) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
        this.traceListener = Objects.requireNonNull(traceListener, "traceListener must not be null");
        this.dialect = JdbcDialects.forType(config.connection().type());
        this.writeSchema = writeSchema;
    }

    @Override
    public SinkWriter createWriter(YakTableSchema schema) {
        return new JdbcSinkWriter(
                config, writeSchema == null ? schema : writeSchema, connectionProvider, dialect, traceListener);
    }
}
