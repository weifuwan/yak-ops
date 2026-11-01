package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.BatchExecutionHook;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real JDBC and runtime recovery across durable before-data, data, and after-data phases. */
class JdbcBatchHooksTest {
    private static final TableId SOURCE = new TableId(null, "PUBLIC", "SOURCE_ROWS");
    private static final TableId TARGET = new TableId(null, "PUBLIC", "TARGET_ROWS");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("ID", LogicalTypes.BIGINT.copy(false)),
                    new Column("LABEL", LogicalTypes.varchar(64))), List.of("ID"));

    @TempDir
    Path state;

    @Test
    void orderedHooksAndDataAreNotRepeatedAfterConfirmedCompletion() throws Exception {
        String url = database();
        JdbcWriteOptions options = new JdbcWriteOptions(500,
                List.of("DELETE FROM TARGET_ROWS", "INSERT INTO TARGET_ROWS VALUES (9, 'before')"),
                List.of("UPDATE TARGET_ROWS SET LABEL = CONCAT(LABEL, '-first')",
                        "UPDATE TARGET_ROWS SET LABEL = CONCAT(LABEL, '-last')"));
        finish(run(url, () -> open(url), options, false));
        assertEquals(List.of("1:one-first-last", "2:two-first-last", "9:before-first-last"), rows(url));
        execute(url, "INSERT INTO SOURCE_ROWS VALUES (3, 'later')");
        finish(run(url, () -> open(url), options, true));
        assertEquals(List.of("1:one-first-last", "2:two-first-last", "9:before-first-last"), rows(url));
    }

    @Test
    void knownPostFailureRestoresOnlyPostPhaseWithoutClearingOrReloadingData() throws Exception {
        String url = database();
        AtomicBoolean first = new AtomicBoolean(true);
        JdbcConnectionProvider provider = () -> statementFailure(open(url), first);
        JdbcWriteOptions options = new JdbcWriteOptions(500, List.of("DELETE FROM TARGET_ROWS"),
                List.of("UPDATE TARGET_ROWS SET LABEL = CONCAT(LABEL, '-done')"));
        fail(run(url, provider, options, false));
        assertEquals(List.of("1:one", "2:two"), rows(url));
        execute(url, "INSERT INTO SOURCE_ROWS VALUES (3, 'must-not-be-imported')");
        finish(run(url, provider, options, true));
        assertEquals(List.of("1:one-done", "2:two-done"), rows(url));
    }

    @Test
    void unknownCommitOutcomeFailsClosedAndNeverReexecutesTheHook() throws Exception {
        String url = database();
        AtomicBoolean first = new AtomicBoolean(true);
        JdbcConnectionProvider provider = () -> lostCommitAcknowledgement(open(url), first);
        JdbcWriteOptions options = new JdbcWriteOptions(500,
                List.of("UPDATE TARGET_ROWS SET LABEL = CONCAT(LABEL, '-uncertain') WHERE ID = 99"), List.of());
        fail(run(url, provider, options, false));
        assertEquals(List.of("99:old-uncertain"), rows(url));
        fail(run(url, provider, options, true));
        assertEquals(List.of("99:old-uncertain"), rows(url));
    }

    @Test
    void differentHookCannotReuseACompletedJournal() throws Exception {
        String url = database();
        finish(run(url, () -> open(url), new JdbcWriteOptions(500, List.of("DELETE FROM TARGET_ROWS"), List.of()), false));
        fail(run(url, () -> open(url), new JdbcWriteOptions(500, List.of("TRUNCATE TABLE TARGET_ROWS"), List.of()), true));
        assertEquals(List.of("1:one", "2:two"), rows(url));
    }

    @Test
    void sqlValidationRejectsCrossTableAndUnboundOrSideEffectingStatementsBeforeIo() {
        JdbcConnectionProvider unopened = () -> { throw new AssertionError("Validation must not open JDBC"); };
        for (String sql : List.of("DROP TABLE TARGET_ROWS", "DELETE FROM TARGET_ROWS; DELETE FROM TARGET_ROWS",
                "INSERT INTO TARGET_ROWS VALUES (?, 'x')", "UPDATE TARGET_ROWS SET LABEL = GET_LOCK('x', 1)")) {
            assertThrows(IllegalArgumentException.class, () -> new JdbcWriteOptions(500, List.of(sql), List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlHooks.operation(unopened, TARGET,
                BatchExecutionHook.Phase.BEFORE_DATA, "DELETE FROM OTHER_ROWS"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlHooks.parseStatements("[1]"));
    }

    private JobClient run(String url, JdbcConnectionProvider targetConnections, JdbcWriteOptions options,
            boolean restore) throws Exception {
        Configuration config = new Configuration();
        config.set(CheckpointingOptions.STATE_DIRECTORY, state.toString());
        config.set(CheckpointingOptions.DEFINITION_IDENTITY, "jdbc-hooks-test-definition-instance");
        config.set(CheckpointingOptions.RESTORE_LATEST, restore);
        config.set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 0);
        JdbcSource source = new JdbcSource(() -> open(url), url, List.of(SOURCE), new Configuration());
        JdbcSink sink = JdbcSink.builder()
                .withConnectionProvider(targetConnections).withDialect(new AnsiJdbcDialect())
                .withTablePlans(List.of(new JdbcTableWritePlan(SOURCE, TARGET, SCHEMA, JdbcWriteMode.APPEND)))
                .withTableWriteOptions(Map.of(SOURCE, options))
                .withBatchFlushPolicy(new BatchFlushPolicy(500, Duration.ZERO)).build();
        SourceTransformation<TableRecord> sourceNode = new SourceTransformation<>("source", source, TableRecord.class, 1);
        sourceNode.setUid("hook-source");
        SinkTransformation<TableRecord> sinkNode = new SinkTransformation<>(sourceNode, "target", sink, 1);
        sinkNode.setUid("hook-sink");
        StreamGraph graph = new StreamGraphGenerator(sinkNode, config).generate();
        return new EmbeddedPipelineExecutor().execute(graph, config).get(5, TimeUnit.SECONDS);
    }

    private static void finish(JobClient job) throws Exception {
        job.getJobExecutionResult().get(15, TimeUnit.SECONDS);
        assertEquals(JobStatus.FINISHED, job.getJobStatus().get(5, TimeUnit.SECONDS));
    }

    private static void fail(JobClient job) throws Exception {
        assertThrows(ExecutionException.class, () -> job.getJobExecutionResult().get(15, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
    }

    private static String database() throws SQLException {
        String url = "jdbc:h2:mem:batch_hooks_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        execute(url, "CREATE TABLE SOURCE_ROWS (ID BIGINT PRIMARY KEY, LABEL VARCHAR(64))");
        execute(url, "CREATE TABLE TARGET_ROWS (ID BIGINT PRIMARY KEY, LABEL VARCHAR(64))");
        execute(url, "INSERT INTO SOURCE_ROWS VALUES (1, 'one'), (2, 'two')");
        execute(url, "INSERT INTO TARGET_ROWS VALUES (99, 'old')");
        return url;
    }

    private static void execute(String url, String sql) throws SQLException {
        try (Connection connection = open(url); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> rows(String url) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = open(url); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT ID, LABEL FROM TARGET_ROWS ORDER BY ID")) {
            while (rows.next()) result.add(rows.getLong(1) + ":" + rows.getString(2));
        }
        return result;
    }

    private static Connection open(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static Connection statementFailure(Connection delegate, AtomicBoolean first) {
        return (Connection) Proxy.newProxyInstance(JdbcBatchHooksTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    Object value = invoke(delegate, method, args);
                    if (!method.getName().equals("createStatement")) return value;
                    Statement statement = (Statement) value;
                    return Proxy.newProxyInstance(JdbcBatchHooksTest.class.getClassLoader(), new Class<?>[] {Statement.class},
                            (statementProxy, statementMethod, statementArgs) -> {
                                if (statementMethod.getName().equals("execute") && statementArgs[0] instanceof String sql
                                        && sql.contains("-done") && first.compareAndSet(true, false)) {
                                    throw new SQLException("Injected rollback-confirmed statement failure", "40001");
                                }
                                return invoke(statement, statementMethod, statementArgs);
                            });
                });
    }

    private static Connection lostCommitAcknowledgement(Connection delegate, AtomicBoolean first) {
        AtomicBoolean hook = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(JdbcBatchHooksTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    Object value = invoke(delegate, method, args);
                    if (method.getName().equals("commit") && hook.get() && first.compareAndSet(true, false)) {
                        throw new SQLException("Injected lost commit acknowledgement", "08006");
                    }
                    if (!method.getName().equals("createStatement")) return value;
                    Statement statement = (Statement) value;
                    return Proxy.newProxyInstance(JdbcBatchHooksTest.class.getClassLoader(), new Class<?>[] {Statement.class},
                            (statementProxy, statementMethod, statementArgs) -> {
                                if (statementMethod.getName().equals("execute") && statementArgs[0] instanceof String sql
                                        && sql.contains("-uncertain")) hook.set(true);
                                return invoke(statement, statementMethod, statementArgs);
                            });
                });
    }

    private static Object invoke(Object delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
