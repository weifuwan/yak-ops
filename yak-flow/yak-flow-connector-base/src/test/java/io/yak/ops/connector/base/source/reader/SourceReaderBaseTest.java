package io.yak.ops.connector.base.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.configuration.Configuration;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** End-to-end reader/fetcher behavior using a controllably blocking, non-JDBC SplitReader. */
class SourceReaderBaseTest {

    @Test
    void readsMultipleSplitsThroughOneFetcher() throws Exception {
        DemoContext context = new DemoContext(new Configuration());
        BlockingSplitReader io = new BlockingSplitReader();
        try (DemoReader reader = new DemoReader(() -> io, context)) {
            reader.start();
            reader.addSplits(List.of(new DemoSplit("orders", 3, 0), new DemoSplit("users", 2, 0)));
            reader.notifyNoMoreSplits();

            List<String> results = readToEnd(reader);
            assertEquals(Set.of("orders:0", "orders:1", "orders:2", "users:0", "users:1"), Set.copyOf(results));
            assertEquals(5, results.size());
            assertEquals(2, reader.finishedCount.get());
            assertTrue(reader.snapshotState(5).isEmpty());
        }
        assertTrue(io.closed.get());
    }

    @Test
    void blockingFetchDoesNotBlockMailboxAndAvailabilityWakesIt() throws Exception {
        BlockingSplitReader io = new BlockingSplitReader();
        io.blockFetch = true;
        try (DemoReader reader = new DemoReader(() -> io, new DemoContext(new Configuration()))) {
            reader.start();
            reader.addSplits(List.of(new DemoSplit("orders", 1, 0)));
            reader.notifyNoMoreSplits();
            assertTrue(io.fetchEntered.await(3, TimeUnit.SECONDS));

            assertTimeoutPreemptively(
                    Duration.ofSeconds(1),
                    () -> assertEquals(InputStatus.NOTHING_AVAILABLE, reader.pollNext(ignored -> {})));
            assertFalse(reader.isAvailable().isDone());

            io.releaseFetch.countDown();
            reader.isAvailable().get(3, TimeUnit.SECONDS);
            assertEquals(List.of("orders:0"), readToEnd(reader));
        }
    }

    @Test
    void snapshotTracksConsumedRecordsRatherThanPrefetch() throws Exception {
        Configuration config = new Configuration();
        config.set(SourceReaderOptions.ELEMENT_QUEUE_CAPACITY, 2);
        BlockingSplitReader io = new BlockingSplitReader();
        try (DemoReader reader = new DemoReader(() -> io, new DemoContext(config))) {
            reader.start();
            reader.addSplits(List.of(new DemoSplit("orders", 8, 0)));
            reader.notifyNoMoreSplits();
            assertTrue(awaitFetchCount(io, 2));

            List<String> firstRecord = new ArrayList<>();
            while (firstRecord.isEmpty()) {
                InputStatus status = reader.pollNext(firstRecord::add);
                if (status == InputStatus.NOTHING_AVAILABLE) {
                    reader.isAvailable().get(3, TimeUnit.SECONDS);
                }
            }

            assertEquals(List.of("orders:0"), firstRecord);
            assertEquals(List.of(new DemoSplit("orders", 8, 1)), reader.snapshotState(7));
            assertEquals(7, readToEnd(reader).size());
            assertTrue(reader.snapshotState(8).isEmpty());
        }
    }

    @Test
    void restoredSplitResumesFromItsSnapshotPosition() throws Exception {
        BlockingSplitReader io = new BlockingSplitReader();
        try (DemoReader reader = new DemoReader(() -> io, new DemoContext(new Configuration()))) {
            reader.addSplits(List.of(new DemoSplit("orders", 4, 2)));
            reader.start();
            reader.notifyNoMoreSplits();
            assertEquals(List.of("orders:2", "orders:3"), readToEnd(reader));
        }
    }

    @Test
    void fetchFailureWakesWaitingMailboxAndPropagatesCause() throws Exception {
        BlockingSplitReader io = new BlockingSplitReader();
        io.failFetch = true;
        try (DemoReader reader = new DemoReader(() -> io, new DemoContext(new Configuration()))) {
            reader.start();
            reader.addSplits(List.of(new DemoSplit("failed", 1, 0)));
            assertTrue(io.fetchEntered.await(3, TimeUnit.SECONDS));
            reader.isAvailable().get(3, TimeUnit.SECONDS);

            IllegalStateException exception =
                    assertThrows(IllegalStateException.class, () -> reader.pollNext(ignored -> {}));
            assertTrue(exception.getCause() instanceof IOException);
            assertEquals("planned read failure", exception.getCause().getMessage());
        }
    }

    @Test
    void closeWakesFetcherBlockedByFullQueue() throws Exception {
        Configuration config = new Configuration();
        config.set(SourceReaderOptions.ELEMENT_QUEUE_CAPACITY, 1);
        BlockingSplitReader io = new BlockingSplitReader();
        DemoReader reader = new DemoReader(() -> io, new DemoContext(config));
        try {
            reader.start();
            reader.addSplits(List.of(new DemoSplit("orders", 20, 0)));
            assertTrue(awaitFetchCount(io, 2));
            assertTimeoutPreemptively(Duration.ofSeconds(3), reader::close);
            assertTrue(io.closed.get());
        } finally {
            reader.close();
        }
    }


    @Test
    void closeUsesTerminalCancellationForBlockedSplitReader() throws Exception {
        BlockingSplitReader io = new BlockingSplitReader();
        io.blockFetch = true;
        DemoReader reader = new DemoReader(() -> io, new DemoContext(new Configuration()));
        try {
            reader.start();
            reader.addSplits(List.of(new DemoSplit("orders", 1, 0)));
            assertTrue(io.fetchEntered.await(3, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(3), reader::close);
            assertEquals(1, io.cancelCalls.get());
            assertTrue(io.closed.get());
        } finally {
            reader.close();
        }
    }

    @Test
    void noMoreSplitsWithoutAssignmentsEndsImmediately() throws Exception {
        try (DemoReader reader = new DemoReader(BlockingSplitReader::new, new DemoContext(new Configuration()))) {
            reader.start();
            reader.notifyNoMoreSplits();
            assertEquals(InputStatus.END_OF_INPUT, reader.pollNext(ignored -> {}));
        }
    }

    @Test
    void duplicateSplitIdInActiveReaderIsRejected() throws Exception {
        BlockingSplitReader io = new BlockingSplitReader();
        try (DemoReader reader = new DemoReader(() -> io, new DemoContext(new Configuration()))) {
            reader.start();
            reader.addSplits(List.of(new DemoSplit("same", 2, 0)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> reader.addSplits(List.of(new DemoSplit("same", 3, 0))));
        }
    }

    private static boolean awaitFetchCount(BlockingSplitReader reader, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (reader.fetchCalls.get() >= expected) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static List<String> readToEnd(DemoReader reader) throws Exception {
        List<String> records = new ArrayList<>();
        for (int attempt = 0; attempt < 1_000; attempt++) {
            InputStatus status = reader.pollNext(records::add);
            if (status == InputStatus.END_OF_INPUT) {
                return records;
            }
            if (status == InputStatus.NOTHING_AVAILABLE) {
                reader.isAvailable().get(3, TimeUnit.SECONDS);
            }
        }
        fail("SourceReader did not reach end-of-input");
        return records;
    }

    private record DemoSplit(String splitId, int total, int position) implements SourceSplit {}

    private record DemoRecord(String splitId, int number, int nextPosition) {}

    private static final class DemoState {
        private final int total;
        private int position;

        private DemoState(DemoSplit split) {
            this.total = split.total();
            this.position = split.position();
        }
    }

    private static final class DemoReader
            extends SingleThreadMultiplexSourceReaderBase<DemoRecord, String, DemoSplit, DemoState> {

        private final AtomicInteger finishedCount = new AtomicInteger();

        private DemoReader(Supplier<? extends SplitReader<DemoRecord, DemoSplit>> supplier, DemoContext context) {
            super(
                    supplier,
                    (record, output, state) -> {
                        output.collect(record.splitId() + ":" + record.number());
                        state.position = record.nextPosition();
                    },
                    context);
        }

        @Override
        protected DemoState initializedState(DemoSplit split) {
            return new DemoState(split);
        }

        @Override
        protected DemoSplit toSplitType(String splitId, DemoState state) {
            return new DemoSplit(splitId, state.total, state.position);
        }

        @Override
        protected void onSplitFinished(Map<String, DemoState> finished) {
            finishedCount.addAndGet(finished.size());
        }
    }

    private static final class DemoContext implements SourceReaderContext {
        private final Configuration configuration;

        private DemoContext(Configuration configuration) {
            this.configuration = new Configuration(configuration);
        }

        @Override
        public Configuration getConfiguration() {
            return new Configuration(configuration);
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

    private static final class BlockingSplitReader implements SplitReader<DemoRecord, DemoSplit> {
        private final Deque<DemoSplit> pending = new ArrayDeque<>();
        private final AtomicInteger fetchCalls = new AtomicInteger();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CountDownLatch fetchEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFetch = new CountDownLatch(1);
        private volatile boolean blockFetch;
        private volatile boolean failFetch;
        private DemoSplit current;
        private int nextPosition;

        @Override
        public void addSplits(List<DemoSplit> splits) {
            pending.addAll(splits);
        }

        @Override
        public RecordsWithSplitIds<DemoRecord> fetch() throws Exception {
            fetchCalls.incrementAndGet();
            fetchEntered.countDown();
            if (blockFetch && !releaseFetch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out blocking fake I/O");
            }
            if (failFetch) {
                throw new IOException("planned read failure");
            }
            if (current == null) {
                current = pending.removeFirst();
                nextPosition = current.position();
            }
            String splitId = current.splitId();
            if (nextPosition >= current.total()) {
                current = null;
                return new RecordsBySplits<>(Map.of(), Set.of(splitId));
            }
            DemoRecord record = new DemoRecord(splitId, nextPosition, ++nextPosition);
            boolean finished = nextPosition == current.total();
            if (finished) {
                current = null;
            }
            return new RecordsBySplits<>(
                    Map.of(splitId, List.of(record)), finished ? Set.of(splitId) : Set.of());
        }

        @Override
        public void wakeUp() {
            releaseFetch.countDown();
        }

        @Override
        public void cancel() {
            cancelCalls.incrementAndGet();
            releaseFetch.countDown();
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
