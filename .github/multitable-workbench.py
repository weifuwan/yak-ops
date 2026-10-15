from pathlib import Path

root = Path('yak-flow/yak-flow-connector-jdbc/src')
main = root / 'main/java/io/yak/ops/connector/jdbc'

def edit(path, old, new, count=1):
    text = path.read_text()
    assert text.count(old) == count, (str(path), old[:100], text.count(old), count)
    path.write_text(text.replace(old, new))

def create(path, content):
    assert not path.exists(), path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)

create(main / 'sink/JdbcBatchOrder.java', '''package io.yak.ops.connector.jdbc.sink;

/** Physical ordering of one bounded Writer batch; not a persisted task write mode. */
public enum JdbcBatchOrder {
    /** Preserve the complete input order, including dependencies between tables. */
    INPUT_ORDER,
    /** Group explicitly independent tables while preserving all operations within each table. */
    INDEPENDENT_TABLES
}
''')

p = main / 'sink/JdbcSinkOptions.java'
edit(p, '    private JdbcSinkOptions() {}', '''    /** Physical opt-in; callers must establish that target tables can be reordered independently. */
    public static final Option<JdbcBatchOrder> BATCH_ORDER = Options.key("connector.jdbc.sink.batch-order")
            .singleChoice(JdbcBatchOrder.class, List.of(JdbcBatchOrder.values()))
            .defaultValue(JdbcBatchOrder.INPUT_ORDER);

    /** Maximum live PreparedStatements per Writer, across all table routes and mutation kinds. */
    public static final Option<Integer> STATEMENT_CACHE_SIZE = Options.key("connector.jdbc.sink.statement-cache-size")
            .intType()
            .defaultValue(64);

    private JdbcSinkOptions() {}''')

p = main / 'internal/executor/TableBufferedStatementExecutor.java'
edit(p, 'import java.util.ArrayList;', 'import java.util.ArrayList;\nimport java.util.Comparator;\nimport java.util.LinkedHashMap;\nimport java.util.Map;\nimport java.util.function.Function;')
edit(p, '    private final UnaryOperator<T> snapshot;', '    private final UnaryOperator<T> snapshot;\n    private final Function<T, ?> groupingKey;')
edit(p, '''        this.statementExecutor = Objects.requireNonNull(statementExecutor, "statementExecutor");''', '''        this(statementExecutor, snapshot, null);
    }

    /**
     * Allows stable grouping only for independently writable destinations.
     *
     * <p>The retained list remains the only owner of records. Sorting preserves the order
     * within each group and does not reduce, deduplicate or copy the row payloads.
     *
     * @param statementExecutor ordered statement executor on the owning transaction
     * @param snapshot detach and validate records at admission
     * @param groupingKey independent destination identity, or null to preserve input order
     */
    public TableBufferedStatementExecutor(
            JdbcBatchStatementExecutor<T> statementExecutor, UnaryOperator<T> snapshot, Function<T, ?> groupingKey) {
        this.groupingKey = groupingKey;
        this.statementExecutor = Objects.requireNonNull(statementExecutor, "statementExecutor");''')
edit(p, '''    public void executeBatch() throws SQLException {
        for (T value : pending) {''', '''    public void executeBatch() throws SQLException {
        if (groupingKey != null) {
            Map<Object, Integer> groups = new LinkedHashMap<>();
            for (T value : pending) {
                Object key = Objects.requireNonNull(groupingKey.apply(value), "batch grouping key");
                groups.computeIfAbsent(key, ignored -> groups.size());
            }
            pending.sort(Comparator.comparingInt(value -> groups.get(groupingKey.apply(value))));
        }
        for (T value : pending) {''')

p = main / 'internal/executor/TableChangelogStatementExecutor.java'
edit(p, 'import java.util.LinkedHashMap;', 'import java.util.LinkedHashMap;\nimport java.util.LinkedHashSet;\nimport java.util.Set;')
edit(p, '    private TableSimpleStatementExecutor active;', '''    private final int statementCacheSize;
    private final Set<TableSimpleStatementExecutor> prepared = new LinkedHashSet<>();
    private Connection connection;
    private TableSimpleStatementExecutor active;''')
edit(p, '''    public TableChangelogStatementExecutor(JdbcDialect dialect, List<JdbcTableWritePlan> plans) {
        Objects.requireNonNull(dialect, "dialect");''', '''    public TableChangelogStatementExecutor(JdbcDialect dialect, List<JdbcTableWritePlan> plans) {
        this(dialect, plans, 64);
    }

    /** Bounds live statements across all routes; SQL and converters remain immutable route definitions. */
    public TableChangelogStatementExecutor(JdbcDialect dialect, List<JdbcTableWritePlan> plans, int statementCacheSize) {
        if (statementCacheSize < 1) {
            throw new IllegalArgumentException("JDBC statement cache size must be positive");
        }
        this.statementCacheSize = statementCacheSize;
        Objects.requireNonNull(dialect, "dialect");''')
text = p.read_text()
start = text.index('    @Override\n    public void prepareStatements(Connection connection)')
end = text.index('    @Override\n    public void addToBatch(TableRecord record)', start)
text = text[:start] + '''    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        if (this.connection != null) {
            throw new IllegalStateException("JDBC executor already owns a connection");
        }
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /** Called only after switching away from and executing the previous active batch. */
    private void prepare(TableSimpleStatementExecutor statement) throws SQLException {
        if (connection == null) {
            throw new IllegalStateException("JDBC executor has no connection");
        }
        if (prepared.remove(statement)) {
            prepared.add(statement);
            return;
        }
        if (prepared.size() == statementCacheSize) {
            TableSimpleStatementExecutor oldest = prepared.iterator().next();
            oldest.closeStatements();
            prepared.remove(oldest);
        }
        statement.prepareStatements(connection);
        prepared.add(statement);
    }

''' + text[end:]
p.write_text(text)
edit(p, '''        next.addToBatch(row);
        active = next;''', '''        prepare(next);
        next.addToBatch(row);
        active = next;''')
edit(p, '''    public void closeStatements() throws SQLException {
        active = null;''', '''    public void closeStatements() throws SQLException {
        connection = null;
        prepared.clear();
        active = null;''')

p = main / 'internal/JdbcOutputFormat.java'
edit(p, 'import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;', 'import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;\nimport io.yak.ops.connector.jdbc.sink.JdbcBatchOrder;\nimport io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;')
edit(p, '''        this.metrics = Objects.requireNonNull(metrics, "metrics");''', '''        this(connections, dialect, plans, metrics, JdbcBatchOrder.INPUT_ORDER,
                JdbcSinkOptions.STATEMENT_CACHE_SIZE.defaultValue());
    }

    /** Creates one transaction with an explicit physical batch order and statement resource bound. */
    public JdbcOutputFormat(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            TableMetrics metrics,
            JdbcBatchOrder batchOrder,
            int statementCacheSize)
            throws SQLException {
        Objects.requireNonNull(batchOrder, "batchOrder");
        this.metrics = Objects.requireNonNull(metrics, "metrics");''')
edit(p, 'new TableChangelogStatementExecutor(dialect, plans);', 'new TableChangelogStatementExecutor(dialect, plans, statementCacheSize);')
edit(p, 'new TableBufferedStatementExecutor<>(statement, statement::snapshot);', '''new TableBufferedStatementExecutor<>(
                        statement, statement::snapshot,
                        batchOrder == JdbcBatchOrder.INDEPENDENT_TABLES ? TableRecord::tableId : null);''')

p = main / 'sink/JdbcSink.java'
edit(p, '    private final TableMetrics metrics;', '    private final TableMetrics metrics;\n    private final JdbcBatchOrder batchOrder;\n    private final int statementCacheSize;')
edit(p, '''        this.metrics = Objects.requireNonNull(metrics, "metrics");''', '''        this(connections, dialect, plans, batchPolicy, metrics, JdbcBatchOrder.INPUT_ORDER,
                JdbcSinkOptions.STATEMENT_CACHE_SIZE.defaultValue());
    }

    /** Creates an explicitly ordered Sink without inferring table independence from UPSERT mode. */
    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy,
            TableMetrics metrics,
            JdbcBatchOrder batchOrder,
            int statementCacheSize) {
        this.batchOrder = Objects.requireNonNull(batchOrder, "batchOrder");
        if (statementCacheSize < 1) {
            throw new IllegalArgumentException("JDBC statement cache size must be positive");
        }
        this.statementCacheSize = statementCacheSize;
        this.metrics = Objects.requireNonNull(metrics, "metrics");''')
edit(p, 'new JdbcOutputFormat(connections, dialect, plans, metrics);', 'new JdbcOutputFormat(connections, dialect, plans, metrics, batchOrder, statementCacheSize);')

p = main / 'sink/JdbcSinkBuilder.java'
edit(p, '    private JdbcWriteMode mode = JdbcWriteMode.APPEND;', '''    private JdbcWriteMode mode = JdbcWriteMode.APPEND;
    private JdbcBatchOrder batchOrder = JdbcBatchOrder.INPUT_ORDER;
    private int statementCacheSize = JdbcSinkOptions.STATEMENT_CACHE_SIZE.defaultValue();''')
edit(p, '''    public JdbcSinkBuilder withBatchFlushPolicy(BatchFlushPolicy policy) {''', '''    /**
     * Selects physical ordering; independent-table grouping requires an explicit caller decision.
     *
     * @param order whole-input order or independent destination groups
     * @return this builder
     */
    public JdbcSinkBuilder withBatchOrder(JdbcBatchOrder order) {
        batchOrder = Objects.requireNonNull(order, "order");
        return this;
    }

    /** Limits live prepared statements for each Writer, not each individual table. */
    public JdbcSinkBuilder withStatementCacheSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("JDBC statement cache size must be positive");
        }
        statementCacheSize = size;
        return this;
    }

    public JdbcSinkBuilder withBatchFlushPolicy(BatchFlushPolicy policy) {''')
edit(p, 'new JdbcSink(provider, resolvedDialect, tablePlans, batchPolicy, metrics);', 'new JdbcSink(provider, resolvedDialect, tablePlans, batchPolicy, metrics, batchOrder, statementCacheSize);')
edit(p, 'new JdbcSink(provider, resolvedDialect, List.of(plan), batchPolicy, metrics);', 'new JdbcSink(provider, resolvedDialect, List.of(plan), batchPolicy, metrics, batchOrder, statementCacheSize);')

p = main / 'sink/JdbcSinkFactory.java'
edit(p, '''                .include(JdbcConnectionOptions.RULE)
                .build();''', '''                .include(JdbcConnectionOptions.RULE)
                .optional(JdbcSinkOptions.BATCH_ORDER, JdbcSinkOptions.STATEMENT_CACHE_SIZE)
                .build();''')
edit(p, '''                .withTablePlans(plans)
                .withTableMetrics(first.metrics())''', '''                .withTablePlans(plans)
                .withBatchOrder(first.options().get(JdbcSinkOptions.BATCH_ORDER))
                .withStatementCacheSize(first.options().get(JdbcSinkOptions.STATEMENT_CACHE_SIZE))
                .withTableMetrics(first.metrics())''')

create(root / 'test/java/io/yak/ops/connector/jdbc/sink/JdbcBatchExecutionTest.java', '''package io.yak.ops.connector.jdbc.sink;

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
''')
