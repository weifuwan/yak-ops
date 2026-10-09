package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcEnumeratorState;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcRecordAndPosition;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceReader;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceSplitReader;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** Regression tests for projected JDBC reads, split boundaries and bounded result batches. */
class JdbcSourceProjectionReliabilityTest {

    @Test
    void projectedSourceHidesPrimaryKeyButResumesFromItsCheckpoint() throws Exception {
        JdbcConnectionOptions database = createRows("jdbc:h2:mem:jdbc_project_resume;DB_CLOSE_DELAY=-1", 10);
        TableId table = new TableId(null, "PUBLIC", "DATASET");
        Configuration options = new Configuration();
        options.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 2);
        JdbcSource source = new JdbcSource(database, List.of(table), options, Map.of(table, List.of("LABEL")));
        PlanningContext planning = new PlanningContext();
        List<JdbcSourceSplit> assigned;
        try (var enumerator = source.createEnumerator(planning)) {
            enumerator.handleSplitRequest(0);
            enumerator.start();
            assigned = List.copyOf(planning.assigned);
        }
        assertEquals(1, assigned.size());
        assertEquals(List.of("LABEL"), assigned.getFirst().columns());
        assertEquals(List.of("LABEL", "ID"), assigned.getFirst().readColumns());

        JdbcSourceSplit saved;
        List<TableRecord> emitted = new ArrayList<>();
        try (JdbcSourceReader reader = (JdbcSourceReader) source.createReader(new ReaderContext())) {
            reader.addSplits(assigned);
            reader.start();
            reader.notifyNoMoreSplits();
            pollUntil(reader, emitted, 2);
            assertEquals(2, emitted.size());
            assertEquals(1, emitted.getFirst().row().getArity());
            assertEquals("row1", emitted.getFirst().row().getString(0));
            saved = reader.snapshotState(1).getFirst();
            assertEquals(2L, saved.lastEmittedKey());
        }

        List<TableRecord> recovered = new ArrayList<>();
        try (JdbcSourceReader reader = (JdbcSourceReader) source.createReader(new ReaderContext())) {
            reader.addSplits(List.of(saved));
            reader.start();
            reader.notifyNoMoreSplits();
            for (int attempt = 0; attempt < 2000; attempt++) {
                InputStatus status = reader.pollNext(recovered::add);
                if (status == InputStatus.END_OF_INPUT) {
                    break;
                }
                if (status == InputStatus.NOTHING_AVAILABLE) {
                    reader.isAvailable().get(3, TimeUnit.SECONDS);
                }
            }
            assertTrue(reader.snapshotState(2).isEmpty());
        }
        assertEquals(8, recovered.size());
        assertEquals("row3", recovered.getFirst().row().getString(0));
        assertEquals("row10", recovered.getLast().row().getString(0));
        assertTrue(recovered.stream().allMatch(row -> row.row().getArity() == 1));
    }

    @Test
    void closedConnectionBetweenSplitsIsReopenedWithoutReplayingCompletedRows() throws Exception {
        JdbcConnectionOptions database = createRows("jdbc:h2:mem:jdbc_reconnect_splits;DB_CLOSE_DELAY=-1", 4);
        TableId table = new TableId(null, "PUBLIC", "DATASET");
        Configuration options = new Configuration();
        options.set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 1);
        options.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 4);
        List<JdbcSourceSplit> splits =
                new JdbcSplitPlanner(database, new AnsiJdbcDialect(), options).plan(table, 0);
        assertEquals(4, splits.size());

        AtomicInteger opened = new AtomicInteger();
        AtomicReference<Connection> firstConnection = new AtomicReference<>();
        JdbcConnectionProvider provider = () -> {
            Connection connection = database.openConnection();
            if (opened.incrementAndGet() == 1) {
                firstConnection.set(connection);
            }
            return connection;
        };
        List<Long> emitted = new ArrayList<>();
        try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(provider, new AnsiJdbcDialect(), options)) {
            reader.addSplits(splits);
            for (int index = 0; index < splits.size(); index++) {
                if (index == 1) {
                    firstConnection.get().close();
                }
                var batch = reader.fetch();
                assertEquals(splits.get(index).splitId(), batch.nextSplit());
                JdbcRecordAndPosition row = batch.nextRecordFromSplit();
                emitted.add(row.record().row().getLong(0));
                assertEquals(null, batch.nextRecordFromSplit());
                assertTrue(batch.finishedSplits().contains(splits.get(index).splitId()));
            }
        }
        assertEquals(List.of(1L, 2L, 3L, 4L), emitted);
        assertEquals(2, opened.get());
    }

    @Test
    void invalidOrUnknownProjectedColumnsAreRejected() throws Exception {
        JdbcConnectionOptions database = createRows("jdbc:h2:mem:jdbc_projection_invalid;DB_CLOSE_DELAY=-1", 2);
        TableId table = new TableId(null, "PUBLIC", "DATASET");
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSource(database, List.of(table), new Configuration(), Map.of(table, List.of("LABEL", "LABEL"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSource(database, List.of(table), new Configuration(), Map.of(table, List.of())));
        JdbcConnectionProvider provider = database::openConnection;
        JdbcSplitPlanner planner = new JdbcSplitPlanner(
                provider, new AnsiJdbcDialect(), new Configuration(), Map.of(table, List.of("UNKNOWN")));
        assertThrows(SQLException.class, () -> planner.plan(table, 0));
    }

    @Test
    void largeResultSetIsFetchedInBoundedBatches() throws Exception {
        JdbcConnectionOptions database = createRows("jdbc:h2:mem:jdbc_large_fetch;DB_CLOSE_DELAY=-1", 8192);
        TableId table = new TableId(null, "PUBLIC", "DATASET");
        Configuration options = new Configuration();
        options.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 1);
        options.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 64);
        options.set(JdbcSourceOptions.RESULT_SET_FETCH_SIZE, 32);
        JdbcSourceSplit split = new JdbcSplitPlanner(database, new AnsiJdbcDialect(), options)
                .plan(table, 0)
                .getFirst();
        int total = 0;
        boolean finished = false;
        try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(database, new AnsiJdbcDialect(), options)) {
            reader.addSplits(List.of(split));
            for (int attempt = 0; attempt < 200 && !finished; attempt++) {
                var records = reader.fetch();
                int count = 0;
                if (split.splitId().equals(records.nextSplit())) {
                    JdbcRecordAndPosition record;
                    while ((record = records.nextRecordFromSplit()) != null) {
                        count++;
                        assertEquals("row" + (total + count), record.record().row().getString(1));
                    }
                }
                assertTrue(count <= 64);
                total += count;
                finished = records.finishedSplits().contains(split.splitId());
            }
        }
        assertTrue(finished);
        assertEquals(8192, total);
    }

    private static void pollUntil(JdbcSourceReader reader, List<TableRecord> rows, int wanted) throws Exception {
        for (int attempt = 0; attempt < 1000 && rows.size() < wanted; attempt++) {
            InputStatus status = reader.pollNext(rows::add);
            if (status == InputStatus.NOTHING_AVAILABLE) {
                reader.isAvailable().get(3, TimeUnit.SECONDS);
            }
        }
    }

    private static JdbcConnectionOptions createRows(String url, int count) throws Exception {
        JdbcConnectionOptions database = new JdbcConnectionOptions(url, "sa", "");
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE DATASET (ID BIGINT PRIMARY KEY, LABEL VARCHAR(40))");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO DATASET VALUES (?, ?)")) {
                for (int index = 1; index <= count; index++) {
                    insert.setLong(1, index);
                    insert.setString(2, "row" + index);
                    insert.addBatch();
                    if (index % 256 == 0) {
                        insert.executeBatch();
                    }
                }
                if (count % 256 != 0) {
                    insert.executeBatch();
                }
            }
        }
        return database;
    }

    private static final class ReaderContext implements SourceReaderContext {

        @Override
        public Configuration getConfiguration() {
            return new Configuration();
        }

        @Override
        public int getIndexOfSubtask() {
            return 0;
        }

        @Override
        public int currentParallelism() {
            return 1;
        }

        @Override
        public void sendSplitRequest() {}
    }

    private static final class PlanningContext implements SplitEnumeratorContext<JdbcSourceSplit> {

        private final List<JdbcSourceSplit> assigned = new ArrayList<>();

        @Override
        public int currentParallelism() {
            return 1;
        }

        @Override
        public Set<Integer> registeredReaders() {
            return Set.of(0);
        }

        @Override
        public void assignSplit(JdbcSourceSplit split, int subtaskId) {
            assigned.add(split);
        }

        @Override
        public void signalNoMoreSplits(int subtaskId) {}

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            try {
                handler.accept(action.call(), null);
            } catch (Exception failure) {
                handler.accept(null, failure);
            }
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }
    }
}

