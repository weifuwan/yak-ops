package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Counts real JDBC calls instead of relying on timing to verify batching and resource bounds. */
class JdbcBatchExecutionTest {
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("ID", LogicalTypes.BIGINT.copy(false)),
                    new Column("PAYLOAD", LogicalTypes.varchar(20))), List.of("ID"));

    @Test
    void groupsAlternatingIndependentTablesWithoutReducingRows() throws Exception {
        var database = new Fixture(2);
        try (var output = database.output(JdbcBatchOrder.INDEPENDENT_TABLES, 64)) {
            for (int index = 0; index < 1000; index++) output.add(row(index % 2, index / 2));
            assertEquals(0, database.stats.prepares);
            assertEquals(1000, output.bufferedRecords());
            output.flush();
            assertEquals(0, output.bufferedRecords());
        }
        assertEquals(List.of(500, 500), database.stats.batchRows);
        assertEquals(1, database.stats.commits);
        assertEquals(0, database.stats.open);
        assertEquals(500, database.count(0));
        assertEquals(500, database.count(1));
    }

    @Test
    void defaultOutputPreservesAllInputTransitions() throws Exception {
        var database = new Fixture(2);
        try (var output = new JdbcOutputFormat(database.connections(), new AnsiJdbcDialect(), database.plans)) {
            for (int index = 0; index < 1000; index++) output.add(row(index % 2, index / 2));
            output.flush();
        }
        assertEquals(1000, database.stats.batchRows.size());
        assertTrue(database.stats.batchRows.stream().allMatch(rows -> rows == 1));
        assertEquals(1, database.stats.commits);
        assertEquals(500, database.count(0));
        assertEquals(500, database.count(1));
    }

    @Test
    void evictsOnlyExecutedStatementsWithoutCommittingToFreeSpace() throws Exception {
        var database = new Fixture(3);
        try (var output = database.output(JdbcBatchOrder.INPUT_ORDER, 1)) {
            for (int index = 0; index < 12; index++) output.add(row(index % 3, index / 3));
            output.flush();
        }
        assertEquals(1, database.stats.peakOpen);
        assertEquals(12, database.stats.prepares);
        assertEquals(0, database.stats.open);
        assertEquals(1, database.stats.commits);
        for (int table = 0; table < 3; table++) assertEquals(4, database.count(table));
    }

    @Test
    void groupedFailureRollsBackEveryPreviouslyExecutedTable() throws Exception {
        var database = new Fixture(2);
        try (var output = database.output(JdbcBatchOrder.INDEPENDENT_TABLES, 1)) {
            output.add(row(0, 1));
            output.add(row(1, 1));
            output.add(row(0, 2));
            output.add(row(1, 1));
            assertThrows(SQLException.class, output::flush);
            assertThrows(IllegalStateException.class, () -> output.add(row(0, 3)));
        }
        assertEquals(0, database.stats.commits);
        assertEquals(0, database.stats.open);
        assertEquals(0, database.count(0));
        assertEquals(0, database.count(1));
    }

    @Test
    void closingUnflushedGroupedRowsDoesNotPrepareOrCommitStatements() throws Exception {
        var database = new Fixture(2);
        try (var output = database.output(JdbcBatchOrder.INDEPENDENT_TABLES, 1)) {
            output.add(row(0, 1));
            output.add(row(1, 2));
        }
        assertEquals(0, database.stats.prepares);
        assertEquals(0, database.stats.commits);
        assertEquals(0, database.count(0));
        assertEquals(0, database.count(1));
    }

    @Test
    void rejectsNonPositiveStatementLimitsBeforeConnecting() throws Exception {
        var database = new Fixture(1);
        assertThrows(IllegalArgumentException.class, () -> database.output(JdbcBatchOrder.INPUT_ORDER, 0));
        assertThrows(IllegalArgumentException.class, () -> JdbcSink.builder().withStatementCacheSize(0));
        assertEquals(0, database.stats.prepares);
    }

    private static TableRecord row(int table, long id) {
        return new TableRecord(new TableId(null, null, "S" + table), RowKind.INSERT, GenericRowData.of(id, "v" + id));
    }

    private static final class Fixture {
        private final String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        private final Stats stats = new Stats();
        private final List<JdbcTableWritePlan> plans = new ArrayList<>();

        private Fixture(int tables) throws Exception {
            try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
                for (int table = 0; table < tables; table++) {
                    sql.execute("CREATE TABLE T" + table + " (ID BIGINT PRIMARY KEY, PAYLOAD VARCHAR(20))");
                    plans.add(new JdbcTableWritePlan(new TableId(null, null, "S" + table),
                            new TableId(null, null, "T" + table), SCHEMA, JdbcWriteMode.APPEND));
                }
            }
        }

        private JdbcConnectionProvider connections() {
            return () -> track(DriverManager.getConnection(url), stats);
        }

        private JdbcOutputFormat output(JdbcBatchOrder order, int cacheSize) throws SQLException {
            return new JdbcOutputFormat(connections(), new AnsiJdbcDialect(), plans, new TableMetrics(), order, cacheSize);
        }

        private int count(int table) throws SQLException {
            try (var connection = DriverManager.getConnection(url);
                    var sql = connection.createStatement();
                    var rows = sql.executeQuery("SELECT COUNT(*) FROM T" + table)) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static final class Stats {
        private int prepares;
        private int open;
        private int peakOpen;
        private int commits;
        private final List<Integer> batchRows = new ArrayList<>();
    }

    private static Connection track(Connection connection, Stats stats) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        var statement = (PreparedStatement) invoke(connection, method, args);
                        stats.prepares++;
                        stats.open++;
                        stats.peakOpen = Math.max(stats.peakOpen, stats.open);
                        return track(statement, stats);
                    }
                    Object result = invoke(connection, method, args);
                    if (method.getName().equals("commit")) stats.commits++;
                    return result;
                });
    }

    private static PreparedStatement track(PreparedStatement statement, Stats stats) {
        int[] rows = {0};
        AtomicBoolean closed = new AtomicBoolean();
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("executeBatch")) stats.batchRows.add(rows[0]);
                    Object result = invoke(statement, method, args);
                    switch (method.getName()) {
                        case "addBatch" -> rows[0]++;
                        case "executeBatch" -> rows[0] = 0;
                        case "close" -> {
                            if (closed.compareAndSet(false, true)) stats.open--;
                        }
                        default -> { }
                    }
                    return result;
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
