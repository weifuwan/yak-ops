package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcEnumeratorState;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSourceEnumerator;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** Checks checkpoint-visible pending splits when assignment or asynchronous planning is interrupted. */
class JdbcSourceEnumeratorReliabilityTest {

    @Test
    void failedAssignmentKeepsWorkInEnumeratorCheckpoint() throws Exception {
        JdbcConnectionOptions db = database("jdbc:h2:mem:enum_assign;DB_CLOSE_DELAY=-1");
        TableId table = new TableId(null, "PUBLIC", "DATASET");
        TestContext context = new TestContext();
        JdbcSplitPlanner planner = new JdbcSplitPlanner(db, new AnsiJdbcDialect(), new Configuration());
        try (JdbcSourceEnumerator enumerator =
                new JdbcSourceEnumerator(context, planner, List.of(table), "fingerprint", null)) {
            enumerator.start();
            assertEquals(1, enumerator.snapshotState(1).pendingSplits().size());
            context.failAssignment = true;
            assertThrows(IllegalStateException.class, () -> enumerator.handleSplitRequest(0));
            JdbcEnumeratorState checkpoint = enumerator.snapshotState(2);
            assertEquals(1, checkpoint.pendingSplits().size());
            assertEquals(1, checkpoint.nextTableIndex());

            context.failAssignment = false;
            enumerator.handleSplitRequest(0);
            assertEquals(1, context.assigned.size());
            assertEquals(0, enumerator.snapshotState(3).pendingSplits().size());
            assertTrue(context.finished);
        }
    }

    @Test
    void snapshotDuringInFlightPlanningReplansTheSameTableOnRestore() throws Exception {
        JdbcConnectionOptions db = database("jdbc:h2:mem:enum_pending;DB_CLOSE_DELAY=-1");
        TableId table = new TableId(null, "PUBLIC", "DATASET");
        TestContext context = new TestContext();
        context.delayPlanning = true;
        JdbcSplitPlanner planner = new JdbcSplitPlanner(db, new AnsiJdbcDialect(), new Configuration());
        JdbcEnumeratorState checkpoint;
        try (JdbcSourceEnumerator enumerator =
                new JdbcSourceEnumerator(context, planner, List.of(table), "fingerprint", null)) {
            enumerator.start();
            checkpoint = enumerator.snapshotState(4);
            assertEquals(0, checkpoint.nextTableIndex());
            assertTrue(checkpoint.pendingSplits().isEmpty());
        }
        TestContext recoveredContext = new TestContext();
        try (JdbcSourceEnumerator restored =
                new JdbcSourceEnumerator(recoveredContext, planner, List.of(table), "fingerprint", checkpoint)) {
            restored.start();
            assertEquals(1, restored.snapshotState(5).nextTableIndex());
            assertEquals(1, restored.snapshotState(5).pendingSplits().size());
        }
    }

    private JdbcConnectionOptions database(String url) throws Exception {
        JdbcConnectionOptions db = new JdbcConnectionOptions(url, "sa", "");
        try (Connection conn = db.openConnection(); Statement sql = conn.createStatement()) {
            sql.execute("CREATE TABLE DATASET (ID BIGINT PRIMARY KEY, LABEL VARCHAR(40))");
            sql.execute("INSERT INTO DATASET VALUES (1, 'first')");
        }
        return db;
    }

    private static final class TestContext implements SplitEnumeratorContext<JdbcSourceSplit> {

        private final List<JdbcSourceSplit> assigned = new ArrayList<>();
        private boolean failAssignment;
        private boolean delayPlanning;
        private boolean finished;

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
            if (failAssignment) {
                throw new IllegalStateException("assignment transport rejected the split");
            }
            assigned.add(split);
        }

        @Override
        public void signalNoMoreSplits(int subtaskId) {
            finished = true;
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            if (delayPlanning) {
                return;
            }
            try {
                handler.accept(action.call(), null);
            } catch (Exception problem) {
                handler.accept(null, problem);
            }
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }
    }
}
