package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.connector.jdbc.sink.writer.JdbcWriter;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Defines one or more JDBC source-to-target table routes for a reusable Sink.
 *
 * <p>Each execution attempt creates its own {@link JdbcWriter} with a distinct JDBC connection
 * and transaction. APPEND accepts only inserts; native UPSERT applies key-aware changelog
 * records. Split UPDATE_BEFORE/UPDATE_AFTER pairs must be adjacent on the same subtask.
 *
 * <p>Flushes are synchronous and at-least-once; there is no XA committer, exactly-once
 * guarantee, or global ordering across parallel Sink subtasks.
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

    /**
     * Validates a multi-table Sink definition before opening any JDBC connection.
     *
     * <p>Source and target tables must be unique. Native UPSERT and DELETE SQL support
     * are validated through the dialect at construction time.
     *
     * @param connections capability that opens independent connections for each Writer
     * @param dialect reusable vendor-specific SQL and row-conversion rules
     * @param plans nonempty source-to-target table routes
     * @param batchPolicy size and optional mailbox processing-time flush policy
     */
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

    /** Starts configuring a single-table or multi-table JDBC Sink. */
    public static JdbcSinkBuilder builder() {
        return new JdbcSinkBuilder();
    }

    /**
     * Creates an independent task-local Writer and releases its resources if initialization fails.
     *
     * <p>The Runtime provides the mailbox timer and the actual Sink subtask identity.
     * This definition never caches the returned Writer across execution attempts.
     */
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
