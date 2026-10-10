package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableRecord;
import java.util.Objects;

/**
 * Single-table JDBC Sink that constructs a fresh Writer for each task attempt.
 *
 * <p>It offers APPEND and native UPSERT for INSERT records, without XA,
 * committers, automatic JDBC replay or a transactional exactly-once claim.
 */
public final class JdbcSink implements Sink<TableRecord> {

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final JdbcTableWritePlan plan;
    private final BatchFlushPolicy batchPolicy;

    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            JdbcTableWritePlan plan,
            BatchFlushPolicy batchPolicy) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.batchPolicy = Objects.requireNonNull(batchPolicy, "batchPolicy");
        plan.sql(dialect);
    }

    public static JdbcSinkBuilder builder() {
        return new JdbcSinkBuilder();
    }

    @Override
    public JdbcWriter createWriter(WriterInitContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        return new JdbcWriter(new JdbcOutputFormat(connections, dialect, plan), batchPolicy, context);
    }
}
