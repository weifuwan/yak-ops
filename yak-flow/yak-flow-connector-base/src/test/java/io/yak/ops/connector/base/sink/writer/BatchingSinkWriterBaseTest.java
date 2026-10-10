package io.yak.ops.connector.base.sink.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.ProcessingTimeService;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.configuration.Configuration;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

/** Verifies that the base Writer triggers flushes without owning a second batch buffer. */
class BatchingSinkWriterBaseTest {

    private static final SinkWriter.Context RECORD_CONTEXT = new SinkWriter.Context() {
        @Override
        public Long timestamp() {
            return null;
        }

        @Override
        public long currentWatermark() {
            return Long.MIN_VALUE;
        }
    };

    @Test
    void sizeThresholdFlushesOnlyTheOutputOwnedBuffer() throws Exception {
        TestOutput output = new TestOutput();
        TestWriter writer = new TestWriter(output, new BatchFlushPolicy(3, Duration.ZERO), context(null));
        writer.write(1, RECORD_CONTEXT);
        writer.write(2, RECORD_CONTEXT);
        assertTrue(output.committed.isEmpty());
        assertEquals(List.of(1, 2), output.pending);

        writer.write(3, RECORD_CONTEXT);
        assertEquals(List.of(List.of(1, 2, 3)), output.committed);
        assertTrue(output.pending.isEmpty());

        writer.write(4, RECORD_CONTEXT);
        writer.flush(false);
        assertEquals(List.of(List.of(1, 2, 3), List.of(4)), output.committed);
        writer.write(5, RECORD_CONTEXT);
        writer.flush(true);
        assertEquals(List.of(List.of(1, 2, 3), List.of(4), List.of(5)), output.committed);
        assertThrows(IllegalStateException.class, () -> writer.write(6, RECORD_CONTEXT));
        writer.close();
        writer.close();
        assertEquals(1, output.closes);
    }

    @Test
    void timedFlushUsesProvidedTaskTimerAndCancelsItAfterCheckpointFlush() throws Exception {
        TestClock clock = new TestClock();
        TestOutput output = new TestOutput();
        TestWriter writer = new TestWriter(output, new BatchFlushPolicy(10, Duration.ofMillis(25)), context(clock));
        writer.write(1, RECORD_CONTEXT);
        assertEquals(1025L, clock.lastTimestamp);
        assertEquals(1, clock.timers.size());
        clock.fire(0);
        assertEquals(List.of(List.of(1)), output.committed);

        writer.write(2, RECORD_CONTEXT);
        writer.flush(false);
        assertTrue(clock.timers.get(1).cancelled);
        clock.fire(1);
        assertEquals(List.of(List.of(1), List.of(2)), output.committed);
        writer.close();
    }

    @Test
    void cancelledOrClosedWriterNeverFlushesItsPendingRecords() throws Exception {
        TestClock clock = new TestClock();
        TestOutput output = new TestOutput();
        TestWriter writer = new TestWriter(output, new BatchFlushPolicy(10, Duration.ofMillis(25)), context(clock));
        writer.write(1, RECORD_CONTEXT);
        writer.cancel();
        writer.cancel();
        clock.fire(0);
        assertTrue(output.committed.isEmpty());
        assertEquals(1, output.cancelSignals);
        assertThrows(CancellationException.class, () -> writer.write(2, RECORD_CONTEXT));
        writer.close();
        assertEquals(1, output.closes);
        assertEquals(List.of(1), output.pending);
        assertTrue(output.committed.isEmpty());
    }

    @Test
    void failedOutputFlushPreventsSilentContinuedWritesOrRetries() throws Exception {
        TestOutput output = new TestOutput();
        output.failFlush = true;
        TestWriter writer = new TestWriter(output, new BatchFlushPolicy(2, Duration.ZERO), context(null));
        writer.write(1, RECORD_CONTEXT);
        IOException problem = assertThrows(IOException.class, () -> writer.write(2, RECORD_CONTEXT));
        assertEquals("external batch failed", problem.getMessage());
        assertEquals(1, output.flushAttempts);
        assertEquals(List.of(1, 2), output.pending);
        assertThrows(IllegalStateException.class, () -> writer.flush(false));
        assertThrows(IllegalStateException.class, () -> writer.write(3, RECORD_CONTEXT));
        assertEquals(1, output.flushAttempts);
        writer.close();
        assertEquals(1, output.closes);
    }

    @Test
    void scheduledFlushExceptionRemainsObservableAndDisablesWriter() throws Exception {
        TestClock clock = new TestClock();
        TestOutput output = new TestOutput();
        output.failFlush = true;
        TestWriter writer = new TestWriter(output, new BatchFlushPolicy(10, Duration.ofMillis(25)), context(clock));
        writer.write(1, RECORD_CONTEXT);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> clock.fire(0));
        assertTrue(failure.getMessage().contains("Scheduled Sink batch flush failed"));
        assertEquals("external batch failed", failure.getCause().getMessage());
        assertThrows(IllegalStateException.class, () -> writer.write(2, RECORD_CONTEXT));
        writer.close();
        assertEquals(1, output.flushAttempts);
    }

    @Test
    void defersAutomaticFlushForAnIncompleteRecordSequence() throws Exception {
        TestClock clock = new TestClock();
        TestOutput output = new TestOutput();
        TestWriter writer = new TestWriter(output, new BatchFlushPolicy(1, Duration.ofMillis(25)), context(clock));
        output.allowAutomaticFlush = false;
        writer.write(1, RECORD_CONTEXT);
        assertTrue(output.committed.isEmpty());
        assertEquals(0, clock.timers.size());

        output.allowAutomaticFlush = true;
        writer.write(2, RECORD_CONTEXT);
        assertEquals(List.of(List.of(1, 2)), output.committed);
        writer.close();
    }

    @Test
    void timedCallbackCannotCommitAnIncompleteRecordSequence() throws Exception {
        TestClock clock = new TestClock();
        TestOutput output = new TestOutput();
        TestWriter writer = new TestWriter(output, new BatchFlushPolicy(10, Duration.ofMillis(25)), context(clock));
        writer.write(1, RECORD_CONTEXT);
        output.allowAutomaticFlush = false;
        clock.fire(0);
        assertTrue(output.committed.isEmpty());
        output.allowAutomaticFlush = true;
        writer.write(2, RECORD_CONTEXT);
        clock.fire(1);
        assertEquals(List.of(List.of(1, 2)), output.committed);
        writer.close();
    }

    @Test
    void rejectsInvalidPolicyAndTimerlessTimedWriter() {
        assertThrows(IllegalArgumentException.class, () -> new BatchFlushPolicy(0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new BatchFlushPolicy(10, Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> new BatchFlushPolicy(10, Duration.ofNanos(1)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> new TestWriter(new TestOutput(), new BatchFlushPolicy(10, Duration.ofMillis(1)), context(null)));
    }

    private static WriterInitContext context(ProcessingTimeService service) {
        return new WriterInitContext() {
            @Override
            public TaskInfo getTaskInfo() {
                return new TaskInfo() {
                    @Override
                    public int getIndexOfThisSubtask() {
                        return 0;
                    }

                    @Override
                    public int getNumberOfParallelSubtasks() {
                        return 1;
                    }

                    @Override
                    public int getAttemptNumber() {
                        return 0;
                    }

                    @Override
                    public int getMaxNumberOfParallelSubtasks() {
                        return 128;
                    }
                };
            }

            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }

            @Override
            public ProcessingTimeService getProcessingTimeService() {
                if (service == null) {
                    return WriterInitContext.super.getProcessingTimeService();
                }
                return service;
            }
        };
    }

    private static final class TestWriter extends BatchingSinkWriterBase<Integer> {
        private TestWriter(BatchOutput<Integer> output, BatchFlushPolicy policy, WriterInitContext context) {
            super(output, policy, context);
        }
    }

    private static final class TestOutput implements BatchOutput<Integer> {
        private final List<Integer> pending = new ArrayList<>();
        private final List<List<Integer>> committed = new ArrayList<>();
        private boolean failFlush;
        private boolean allowAutomaticFlush = true;
        private int flushAttempts;
        private int cancelSignals;
        private int closes;

        @Override
        public void add(Integer record) {
            pending.add(record);
        }

        @Override
        public int bufferedRecords() {
            return pending.size();
        }

        @Override
        public boolean canAutomaticallyFlush() {
            return allowAutomaticFlush;
        }

        @Override
        public void flush() throws IOException {
            flushAttempts++;
            if (failFlush) {
                throw new IOException("external batch failed");
            }
            committed.add(List.copyOf(pending));
            pending.clear();
        }

        @Override
        public void cancel() {
            cancelSignals++;
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private static final class TestClock implements ProcessingTimeService {
        private final List<RegisteredTimer> timers = new ArrayList<>();
        private long lastTimestamp;

        @Override
        public long currentProcessingTime() {
            return 1000L;
        }

        @Override
        public TimerHandle registerTimer(long timestampMillis, Runnable callback) {
            lastTimestamp = timestampMillis;
            RegisteredTimer timer = new RegisteredTimer(callback);
            timers.add(timer);
            return () -> {
                if (timer.cancelled) {
                    return false;
                }
                timer.cancelled = true;
                return true;
            };
        }

        void fire(int index) {
            RegisteredTimer timer = timers.get(index);
            if (!timer.cancelled) {
                timer.callback.run();
            }
        }

        private static final class RegisteredTimer {
            private final Runnable callback;
            private boolean cancelled;

            private RegisteredTimer(Runnable callback) {
                this.callback = callback;
            }
        }
    }
}
