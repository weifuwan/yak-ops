package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reusable JDBC Sink definition supporting multiple ordered table routes and Changelog.
 *
 * <p>Each Writer has its own transaction and no shared mutable connection state. The Sink
 * does not promise exactly-once, XA or global ordering across parallel subtasks.
 */
public final class JdbcSink implements Sink<TableRecord> {

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final List<JdbcTableWritePlan> plans;
    private final BatchFlushPolicy batchPolicy;

    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            JdbcTableWritePlan plan,
            BatchFlushPolicy batchPolicy) {
        this(connections, dialect, List.of(plan), batchPolicy);
    }

    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        this.batchPolicy = Objects.requireNonNull(batchPolicy, "batchPolicy");
        if (this.plans.isEmpty()) {
            throw new IllegalArgumentException("JDBC Sink needs at least one table route");
        }
        Set<TableId> sources = new HashSet<>();
        Set<TableId> targets = new HashSet<>();
        for (JdbcTableWritePlan plan : this.plans) {
            if (!sources.add(plan.sourceTable()) || !targets.add(plan.targetTable())) {
                throw new IllegalArgumentException("JDBC Sink source and target table routes must be unique");
            }
            plan.sql(dialect);
            if (plan.writeMode() == JdbcWriteMode.UPSERT) {
                dialect.deleteSql(plan.targetTable(), plan.schema());
            }
        }
    }

    public static JdbcSinkBuilder builder() {
        return new JdbcSinkBuilder();
    }

    @Override
    public JdbcWriter createWriter(WriterInitContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        JdbcOutputFormat output = new JdbcOutputFormat(connections, dialect, plans);
        try {
            return new JdbcWriter(output, batchPolicy, context);
        } catch (RuntimeException | Error failure) {
            try {
                output.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
