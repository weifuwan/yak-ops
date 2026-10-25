package io.yak.ops.connector.jdbc.xa;

import com.mysql.cj.jdbc.MysqlXADataSource;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.connector.sink.Committer;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableRecord;
import java.util.Objects;

/**
 * Single-route MySQL RETRACT Sink coordinating prepared XA transactions with durable checkpoints.
 *
 * <p>Recovered checkpoints must use the same transaction protocol as their original execution.
 */
public final class MySqlXaSink implements SupportsCommitter<TableRecord, MySqlXaXid> {

    private final JdbcConnectionOptions connection;
    private final JdbcDialect dialect;
    private final JdbcTableWritePlan plan;
    private final String instanceId;

    public MySqlXaSink(
            JdbcConnectionOptions connection, JdbcDialect dialect, JdbcTableWritePlan plan, String instanceId) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.plan = Objects.requireNonNull(plan, "plan");
        if (!(dialect instanceof MySqlJdbcDialect) || !connection.url().startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("MySQL XA requires the MySQL JDBC dialect");
        }
        if (connection.configuration().get(JdbcConnectionOptions.SSH_ENABLED)) {
            throw new UnsupportedOperationException("MySQL XA requires a verified direct JDBC XA connection");
        }
        if (plan.writeMode() != JdbcWriteMode.RETRACT) {
            throw new IllegalArgumentException("MySQL XA keyless Sink requires RETRACT");
        }
        if (instanceId == null || !instanceId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("XA requires a stable task instance ID");
        }
        this.instanceId = instanceId;
    }

    @Override
    public CommittingSinkWriter<TableRecord, MySqlXaXid> createWriter(WriterInitContext context) throws Exception {
        if (context.getTaskInfo().getNumberOfParallelSubtasks() != 1) {
            throw new UnsupportedOperationException("Initial MySQL XA keyless Sink requires one writer");
        }
        return new MySqlXaWriter(
                connection, dialect, plan, instanceId, context.getTaskInfo().getIndexOfThisSubtask());
    }

    @Override
    public Committer<MySqlXaXid> createCommitter(WriterInitContext context) {
        return new MySqlXaCommitter(dataSource(connection), instanceId);
    }

    @Override
    public SimpleVersionedSerializer<MySqlXaXid> getCommittableSerializer() {
        return new MySqlXaXidSerializer();
    }

    static MysqlXADataSource dataSource(JdbcConnectionOptions connection) {
        MysqlXADataSource result = new MysqlXADataSource();
        result.setUrl(connection.url());
        result.setUser(connection.username());
        result.setPassword(connection.password());
        return result;
    }
}
