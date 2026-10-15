from pathlib import Path

root = Path('yak-flow/yak-flow-connector-cdc-mysql/src')
main = root / 'main/java/io/yak/ops/connector/cdc/mysql/source'
tests = root / 'test/java/io/yak/ops/connector/cdc/mysql/source'

def edit(path, old, new, count=1):
    text = path.read_text()
    assert text.count(old) == count, (str(path), old[:90], text.count(old))
    path.write_text(text.replace(old, new))

p = main / 'reader/MySqlBinlogSplitFilter.java'
edit(p, 'import java.util.Comparator;', 'import java.util.Comparator;\nimport java.util.HashSet;\nimport java.util.Set;')
edit(p, '    private final Map<TableId, Integer> keyPositions = new LinkedHashMap<>();', '''    private final Map<TableId, Integer> keyPositions = new LinkedHashMap<>();
    private final Map<TableId, Integer> columnCounts = new LinkedHashMap<>();''')
edit(p, '            keyPositions.put(entry.getKey(), index);', '            keyPositions.put(entry.getKey(), index);\n            columnCounts.put(entry.getKey(), schema.columnCount());')
edit(p, '        for (FinishedSnapshotSplitInfo chunk : finished) {', '''        Set<String> splitIds = new HashSet<>();
        for (FinishedSnapshotSplitInfo chunk : finished) {
            if (!splitIds.add(chunk.splitId())) {
                throw new IllegalArgumentException("Duplicate completed snapshot chunk identity");
            }''')
edit(p, '''            for (FinishedSnapshotSplitInfo existing : table) {
                if (existing.splitId().equals(chunk.splitId())) {
                    throw new IllegalArgumentException("Duplicate completed snapshot chunk");
                }
            }
''', '')
edit(p, '''                if (!Objects.equals(
                        infos.get(index - 1).upperExclusive(), infos.get(index).lowerInclusive())) {''', '''                if (infos.get(index - 1).upperExclusive() == null
                        || infos.get(index).lowerInclusive() == null
                        || !Objects.equals(infos.get(index - 1).upperExclusive(), infos.get(index).lowerInclusive())) {''')
old = '''        long key = record.row().getLong(position);
        FinishedSnapshotSplitInfo match = null;
        for (FinishedSnapshotSplitInfo info : infos) {
            if (info.contains(key)) {
                if (match != null) {
                    throw new IllegalStateException("Overlapping finished snapshot key ranges");
                }
                match = info;
            }
        }
        if (match == null) {
            throw new IllegalStateException("Missing finished snapshot metadata for Binlog key");
        }
'''
new = '''        if (record.row().getArity() != columnCounts.get(record.tableId()) || record.row().isNullAt(position)) {
            throw new IllegalArgumentException("Binlog row violates its frozen schema or primary key");
        }
        long key = record.row().getLong(position);
        // Construction proves complete, non-overlapping coverage. Find the greatest
        // inclusive lower bound without scanning every chunk for each Binlog mutation.
        int left = 0;
        int right = infos.size() - 1;
        int matched = 0;
        while (left <= right) {
            int middle = left + (right - left) / 2;
            Long lower = infos.get(middle).lowerInclusive();
            if (lower == null || lower <= key) {
                matched = middle;
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }
        FinishedSnapshotSplitInfo match = infos.get(matched);
        if (!match.contains(key)) {
            throw new IllegalStateException("Missing finished snapshot metadata for Binlog key");
        }
'''
edit(p, old, new)

p = main / 'reader/MySqlChunkBackfill.java'
edit(p, '    private final MySqlSnapshotSplit split;', '''    private static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;
    private final MySqlSnapshotSplit split;
    private final TableSchema schema;
    private final long maxBytes;
    private long retainedBytes;
    private boolean failed;''')
edit(p, '''    public MySqlChunkBackfill(MySqlSnapshotSplit split, TableSchema schema, int maxRows) {
        this.split = Objects.requireNonNull(split, "split");
        Objects.requireNonNull(schema, "schema");''', '''    public MySqlChunkBackfill(MySqlSnapshotSplit split, TableSchema schema, int maxRows) {
        this(split, schema, maxRows, DEFAULT_MAX_BYTES);
    }

    /**
     * Bounds retained rows and conservatively accounted payload bytes for one chunk.
     *
     * @param split frozen chunk identity and range
     * @param schema source column types used for variable-width payload accounting
     * @param maxRows maximum number of normalized rows
     * @param maxBytes retained payload budget, not an exact JVM heap measurement
     */
    public MySqlChunkBackfill(MySqlSnapshotSplit split, TableSchema schema, int maxRows, long maxBytes) {
        this.split = Objects.requireNonNull(split, "split");
        this.schema = Objects.requireNonNull(schema, "schema");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("Chunk backfill must have a positive byte budget");
        }
        this.maxBytes = maxBytes;''')
edit(p, '''        rows.put(key, snapshot(record));
        enforceCapacity();''', '        retain(key, record);')
edit(p, '''            rows.put(key, snapshot(record));
            enforceCapacity();''', '            retain(key, record);')
edit(p, '''        return List.copyOf(new ArrayList<>(rows.values()));''', '''        List<TableRecord> result = List.copyOf(rows.values());
        rows.clear();
        retainedBytes = 0;
        return result;''')
edit(p, '''            rows.remove(key);''', '''            TableRecord removed = rows.remove(key);
            if (removed != null) retainedBytes -= payloadBytes(removed.row());''')
edit(p, '''        return record.row().getLong(keyPosition);''', '''        if (record.row().isNullAt(keyPosition)) {
            throw new IllegalArgumentException("Backfill primary key must not be null");
        }
        return record.row().getLong(keyPosition);''')
edit(p, '''    private void enforceCapacity() {
        if (rows.size() > maxRows) {
            throw new IllegalStateException("Snapshot Backfill row limit exceeded; retry with smaller chunks");
        }
    }''', '''    private void retain(long key, TableRecord record) {
        TableRecord previous = rows.get(key);
        long size = payloadBytes(record.row());
        long nextBytes = retainedBytes - (previous == null ? 0 : payloadBytes(previous.row())) + size;
        if ((previous == null && rows.size() >= maxRows) || nextBytes > maxBytes) {
            failed = true;
            rows.clear();
            retainedBytes = 0;
            throw new IllegalStateException("Snapshot Backfill buffer limit exceeded; use smaller chunks");
        }
        rows.put(key, snapshot(record));
        retainedBytes = nextBytes;
    }

    private long payloadBytes(RowData row) {
        long bytes = 128L + 8L * columns;
        for (int index = 0; index < columns; index++) {
            if (row.isNullAt(index)) continue;
            bytes += switch (schema.column(index).dataType().getTypeRoot()) {
                case CHAR, VARCHAR -> 48L + 2L * row.getString(index).length();
                case BINARY, VARBINARY -> 24L + ((byte[]) row.getField(index)).length;
                default -> 64L;
            };
        }
        return bytes;
    }''')
edit(p, '''        if (sealed) {
            throw new IllegalStateException("Snapshot Backfill has already been finalized");''', '''        if (sealed || failed) {
            throw new IllegalStateException("Snapshot Backfill has already been finalized or failed");''')

p = main / 'reader/MySqlSnapshotSplitReader.java'
edit(p, '    private PreparedStatement statement;', '    private volatile PreparedStatement statement;\n    private volatile boolean cancelled;')
edit(p, '''    public void open(MySqlSnapshotSplit split) throws SQLException {
        if (active != null) {''', '''    public void open(MySqlSnapshotSplit split) throws SQLException {
        checkCancelled();
        if (active != null) {''')
edit(p, '''            statement = connection.prepareStatement(sql);
            statement.setFetchSize(FETCH_BATCH);''', '''            statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            checkCancelled();
            statement.setQueryTimeout(60);
            statement.setFetchSize(FETCH_BATCH);''')
edit(p, '''            long executeStartedNanos = System.nanoTime();
            results = statement.executeQuery();''', '''            checkCancelled();
            long executeStartedNanos = System.nanoTime();
            results = statement.executeQuery();''')
edit(p, '''    public RecordsWithSplitIds<MySqlHybridFetchedRecord> fetch() throws SQLException {
        if (active == null) {''', '''    public RecordsWithSplitIds<MySqlHybridFetchedRecord> fetch() throws SQLException {
        checkCancelled();
        if (active == null) {''')
edit(p, '''        for (int remaining = FETCH_BATCH; hasRow && remaining > 0; remaining--) {
            var row = converter.toInternal(results);''', '''        for (int remaining = FETCH_BATCH; hasRow && remaining > 0; remaining--) {
            checkCancelled();
            var row = converter.toInternal(results);''')
edit(p, '''    @Override
    public void close() throws SQLException {''', '''    /** Signals driver cancellation without making the requesting mailbox wait for network I/O. */
    public void cancel() {
        cancelled = true;
        PreparedStatement running = statement;
        if (running != null) {
            Thread.ofVirtual().name("yak-mysql-snapshot-cancel").start(() -> {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    // The fetcher still owns close and observes the query outcome.
                }
            });
        }
    }

    private void checkCancelled() throws SQLException {
        if (cancelled) throw new SQLException("MySQL snapshot read was cancelled");
    }

    @Override
    public void close() throws SQLException {''')

p = main / 'reader/MySqlHybridSplitReader.java'
edit(p, '''        cancelled = true;
        binlog.requestStop();''', '''        cancelled = true;
        snapshots.cancel();
        binlog.requestStop();''')

p = main / 'MySqlCdcSourceConfig.java'
edit(p, '''        String url = "jdbc:mysql://" + hostname + ":" + port + "/";''', '''        String url = "jdbc:mysql://" + hostname + ":" + port
                + "/?useCursorFetch=true&useServerPrepStmts=true";''')

p = tests / 'reader/MySqlBinlogSplitFilterTest.java'
edit(p, '''    private static MySqlBinlogSplitFilter filter() {''', '''    @Test
    void rejectsTwoUnboundedRangesBeforeConsumingAnyRecord() {
        assertThrows(IllegalArgumentException.class, () -> new MySqlBinlogSplitFilter(Map.of(TABLE, SCHEMA),
                List.of(new FinishedSnapshotSplitInfo(TABLE, "a", null, null, offset(200)),
                        new FinishedSnapshotSplitInfo(TABLE, "b", null, null, offset(300)))));
    }

    @Test
    void rejectsDuplicateSplitIdentityAcrossDifferentTables() {
        TableId other = new TableId("shop", null, "items");
        assertThrows(IllegalArgumentException.class, () -> new MySqlBinlogSplitFilter(
                Map.of(TABLE, SCHEMA, other, SCHEMA),
                List.of(new FinishedSnapshotSplitInfo(TABLE, "same", null, null, offset(200)),
                        new FinishedSnapshotSplitInfo(other, "same", null, null, offset(300)))));
    }

    @Test
    void binaryLookupHonorsHalfOpenBoundsAndLongExtremes() {
        var filter = filter();
        assertEquals(1, filter.filter(event(250, row(RowKind.INSERT, 99, "left"))).records().size());
        assertEquals(0, filter.filter(event(250, row(RowKind.INSERT, 100, "right"))).records().size());
        assertEquals(1, filter.filter(event(350, row(RowKind.INSERT, Long.MIN_VALUE, "min"))).records().size());
        assertEquals(1, filter.filter(event(350, row(RowKind.INSERT, Long.MAX_VALUE, "max"))).records().size());
    }

    @Test
    void resolvesThousandsOfUnsortedChunksToTheirOwnHighWatermarks() {
        var chunks = new java.util.ArrayList<FinishedSnapshotSplitInfo>();
        for (int index = 1023; index >= 0; index--) {
            chunks.add(new FinishedSnapshotSplitInfo(TABLE, "c" + index,
                    index == 0 ? null : (long) index,
                    index == 1023 ? null : (long) index + 1, offset(100 + 2L * index)));
        }
        var filter = new MySqlBinlogSplitFilter(Map.of(TABLE, SCHEMA), chunks);
        for (int index = 0; index < 1024; index++) {
            assertEquals(0, filter.filter(event(100 + 2L * index, row(RowKind.INSERT, index, "covered"))).records().size());
            assertEquals(1, filter.filter(event(101 + 2L * index, row(RowKind.INSERT, index, "new"))).records().size());
        }
    }

    @Test
    void rejectsNullKeysAndMalformedRowShapes() {
        var filter = filter();
        assertThrows(IllegalArgumentException.class, () -> filter.filter(event(350,
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(null, "null-key")))));
        assertThrows(IllegalArgumentException.class, () -> filter.filter(event(350,
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L)))));
    }

    private static MySqlBinlogSplitFilter filter() {''')

p = tests / 'reader/MySqlChunkBackfillTest.java'
edit(p, '''    private static MySqlSnapshotSplit split(Long lower, Long upper) {''', '''    @Test
    void byteLimitFailsClosedEvenBeforeTheRowLimit() {
        var schema = new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("name", LogicalTypes.varchar(10000))), List.of("id"));
        var backfill = new MySqlChunkBackfill(split(null, null), schema, 100, 512);
        backfill.addSnapshot(row(RowKind.INSERT, 1L, "small"));
        assertThrows(IllegalStateException.class,
                () -> backfill.addSnapshot(row(RowKind.INSERT, 2L, "x".repeat(2000))));
        assertThrows(IllegalStateException.class, backfill::finish);
        assertThrows(IllegalStateException.class,
                () -> backfill.apply(change(row(RowKind.DELETE, 1L, "small"))));
    }

    @Test
    void deletesReleaseThePayloadBudgetForSubsequentRows() {
        var backfill = new MySqlChunkBackfill(split(null, null), SCHEMA, 10, 512);
        backfill.addSnapshot(row(RowKind.INSERT, 1L, "one"));
        backfill.apply(change(row(RowKind.DELETE, 1L, "one")));
        backfill.apply(change(row(RowKind.INSERT, 2L, "two")));
        assertEquals(Map.of(2L, "two"), values(backfill.finish()));
    }

    @Test
    void rejectsNullPrimaryKeysBeforeBufferingOrRetracting() {
        var backfill = new MySqlChunkBackfill(split(null, null), SCHEMA, 5);
        var malformed = new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(null, "missing"));
        assertThrows(IllegalArgumentException.class, () -> backfill.addSnapshot(malformed));
        assertEquals(Map.of(), values(backfill.finish()));
    }

    private static MySqlSnapshotSplit split(Long lower, Long upper) {''')

p = tests / 'reader/MySqlSnapshotCancellationTest.java'
assert not p.exists()
p.write_text('''package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Coordinates a blocked driver query with latches instead of sleep-based cancellation tests. */
class MySqlSnapshotCancellationTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
    private static final MySqlSnapshotSplit SPLIT = new MySqlSnapshotSplit("snapshot", "frozen", TABLE, null, null, null);

    @Test
    void cancelsRunningJdbcQueryWithoutBlockingTheCaller() throws Exception {
        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger timeout = new AtomicInteger();
        var statement = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setQueryTimeout" -> timeout.set((Integer) args[0]);
                        case "executeQuery" -> {
                            executing.countDown();
                            if (!cancelled.await(5, TimeUnit.SECONDS)) throw new SQLException("Driver cancellation missing");
                            throw new SQLException("Driver query cancelled");
                        }
                        case "cancel" -> cancelled.countDown();
                        default -> { }
                    }
                    return null;
                });
        var connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> method.getName().equals("prepareStatement") ? statement : null);
        try (var reader = new MySqlSnapshotSplitReader(() -> connection, new AnsiJdbcDialect(), Map.of(TABLE, SCHEMA));
                var executor = Executors.newSingleThreadExecutor()) {
            var opening = executor.submit(() -> { reader.open(SPLIT); return null; });
            try {
                assertTrue(executing.await(5, TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(1), reader::cancel);
                assertTrue(cancelled.await(5, TimeUnit.SECONDS));
                var failure = assertThrows(ExecutionException.class, () -> opening.get(5, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof SQLException);
                assertEquals(60, timeout.get());
                assertThrows(SQLException.class, () -> reader.open(SPLIT));
            } finally {
                cancelled.countDown();
            }
        }
    }

    @Test
    void cancellationBeforeOpenDoesNotAcquireAConnection() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        try (var reader = new MySqlSnapshotSplitReader(() -> {
            connections.incrementAndGet();
            throw new SQLException("Unexpected connection");
        }, new AnsiJdbcDialect(), Map.of(TABLE, SCHEMA))) {
            reader.cancel();
            assertThrows(SQLException.class, () -> reader.open(SPLIT));
            assertEquals(0, connections.get());
        }
    }
}
''')
