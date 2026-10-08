package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.Sink;
import io.yak.ops.flow.api.sink.SinkWriter;
import io.yak.ops.flow.api.source.Boundedness;
import io.yak.ops.flow.api.source.Source;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.api.source.SourceSplit;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次 YakFlow 本地执行，负责 Source/Sink 工作线程、取消、状态以及 barrier 检查点生命周期。
 *
 * @param <SplitT> Source 分片类型
 * @author weifuwan
 * @since 2026-09-27
 */
public final class LocalExecution<SplitT extends SourceSplit> {

    private static final long IDLE_SLEEP_MILLIS = 5L;

    private final Source<SplitT> source;
    private final Sink sink;
    private final YakTableSchema schema;
    private final RowChannel channel;
    private final long autoCheckpointIntervalNanos;
    private final int sourceParallelism;
    private final AtomicReference<ExecutionStatus> status = new AtomicReference<>(ExecutionStatus.CREATED);
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final AtomicBoolean automaticCheckpointPending = new AtomicBoolean();
    private final AtomicLong checkpointSequence = new AtomicLong();
    private final AtomicLong readRows = new AtomicLong();
    private final AtomicLong writeRows = new AtomicLong();
    private final BlockingQueue<CheckpointRequest> checkpointRequests = new LinkedBlockingQueue<>();
    private final BlockingQueue<LocalCheckpoint> completedCheckpoints = new LinkedBlockingQueue<>();
    private final ConcurrentMap<Long, CompletableFuture<LocalCheckpoint>> checkpointFutures = new ConcurrentHashMap<>();
    private final Set<Long> automaticCheckpointIds = ConcurrentHashMap.newKeySet();
    private final Set<Thread> sourceWorkerThreads = ConcurrentHashMap.newKeySet();
    private final AtomicInteger workersRemaining = new AtomicInteger(2);
    private final CountDownLatch executionFinished = new CountDownLatch(1);

    private volatile Throwable failure;
    private volatile LocalCheckpoint latestCheckpoint;
    private volatile Thread sourceThread;
    private volatile Thread sinkThread;
    private long nextAutoCheckpointNanos;

    LocalExecution(
            Source<SplitT> source,
            Sink sink,
            YakTableSchema schema,
            int channelCapacity,
            Duration streamCheckpointInterval,
            int sourceParallelism) {
        this.source = source;
        this.sink = sink;
        this.schema = schema;
        this.channel = new RowChannel(channelCapacity);
        this.autoCheckpointIntervalNanos = streamCheckpointInterval.toNanos();
        this.sourceParallelism = sourceParallelism;
    }

    void start() {
        if (!status.compareAndSet(ExecutionStatus.CREATED, ExecutionStatus.RUNNING)) {
            throw new IllegalStateException("execution has already been started");
        }

        nextAutoCheckpointNanos = System.nanoTime() + autoCheckpointIntervalNanos;
        sourceThread = Thread.ofVirtual()
                .name("yak-flow-source")
                .unstarted(sourceParallelism > 1 ? this::runParallelBoundedSource : this::runSource);
        sinkThread = Thread.ofVirtual().name("yak-flow-sink").unstarted(this::runSink);
        sinkThread.start();
        sourceThread.start();
    }

    /**
     * 返回当前执行状态。
     *
     * @return 执行状态
     */
    public ExecutionStatus status() {
        return status.get();
    }

    /**
     * 返回失败原因；只有 FAILED 状态存在该值。
     *
     * @return 失败原因
     */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    /**
     * 返回当前执行最近一次成功完成的检查点。
     *
     * @return 最近检查点
     */
    public Optional<LocalCheckpoint> latestCheckpoint() {
        return Optional.ofNullable(latestCheckpoint);
    }

    /**
     * 返回当前执行指标快照。
     *
     * @return 当前累计读取和写入行数
     */
    public ExecutionMetrics metrics() {
        return new ExecutionMetrics(readRows.get(), writeRows.get());
    }

    /**
     * 请求一次异步检查点。Source 在线程内生成状态并把 barrier 放入 Channel，Sink flush 后再由 Source 完成外部 offset 确认。
     *
     * @return 检查点完成 Future
     */
    public CompletableFuture<LocalCheckpoint> checkpoint() {
        if (sourceParallelism > 1) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("parallel bounded Source checkpoint is not supported yet"));
        }
        if (status.get() != ExecutionStatus.RUNNING) {
            return CompletableFuture.failedFuture(new IllegalStateException("checkpoint requires a running execution"));
        }

        long checkpointId = checkpointSequence.incrementAndGet();
        CompletableFuture<LocalCheckpoint> future = new CompletableFuture<>();
        checkpointFutures.put(checkpointId, future);
        checkpointRequests.add(new CheckpointRequest(checkpointId, false));

        if (status.get() != ExecutionStatus.RUNNING && checkpointFutures.remove(checkpointId, future)) {
            future.completeExceptionally(new IllegalStateException("execution finished before checkpoint"));
        }
        return future;
    }

    /**
     * 显式取消当前执行。该操作会中断阻塞在 Channel 或 Reader 中的本地工作线程。
     */
    public void cancel() {
        if (!status.compareAndSet(ExecutionStatus.RUNNING, ExecutionStatus.CANCELED)) {
            return;
        }
        cancellationRequested.set(true);
        interruptWorkers();
        completePendingCheckpoints(new CancellationException("execution canceled"));
    }

    /**
     * 一直等待本地执行全部工作线程退出。
     *
     * @return 终止后的执行状态
     * @throws InterruptedException 当前等待线程被中断
     */
    public ExecutionStatus await() throws InterruptedException {
        executionFinished.await();
        return status.get();
    }

    /**
     * 等待本地执行全部工作线程退出。
     *
     * @param timeout 最长等待时间
     * @return 终止后的执行状态
     * @throws InterruptedException 当前等待线程被中断
     */
    public ExecutionStatus await(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (!executionFinished.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("execution did not finish within timeout");
        }
        return status.get();
    }

    private void runSource() {
        try (SourceSplitEnumerator<SplitT> enumerator = source.createEnumerator()) {
            enumerator.start();
            while (!cancellationRequested.get()) {
                processCompletedCheckpoints(null);
                maybeRequestAutomaticCheckpoint();
                processCheckpointRequests(enumerator, null, null);

                Optional<SplitT> nextSplit = enumerator.nextSplit();
                if (nextSplit.isPresent()) {
                    runSplit(enumerator, nextSplit.get());
                    continue;
                }

                if (enumerator.isFinished() && source.boundedness() == Boundedness.BOUNDED) {
                    processCompletedCheckpoints(null);
                    processCheckpointRequests(enumerator, null, null);
                    channel.put(EndOfInputMessage.INSTANCE);
                    return;
                }
                Thread.sleep(IDLE_SLEEP_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!cancellationRequested.get()) {
                fail(e);
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            workerFinished();
        }
    }

    private void runParallelBoundedSource() {
        try (SourceSplitEnumerator<SplitT> enumerator = source.createEnumerator()) {
            enumerator.start();
            Object splitAssignmentLock = new Object();
            List<Thread> readers = new ArrayList<>(sourceParallelism);
            for (int index = 0; index < sourceParallelism && !cancellationRequested.get(); index++) {
                Thread readerThread = Thread.ofVirtual()
                        .name("yak-flow-source-reader-" + index)
                        .unstarted(() -> runParallelSourceReader(enumerator, splitAssignmentLock));
                readers.add(readerThread);
                sourceWorkerThreads.add(readerThread);
                readerThread.start();
            }
            for (Thread reader : readers) {
                reader.join();
            }
            if (!cancellationRequested.get() && status.get() == ExecutionStatus.RUNNING) {
                channel.put(EndOfInputMessage.INSTANCE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!cancellationRequested.get()) {
                fail(e);
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            workerFinished();
        }
    }

    private void runParallelSourceReader(SourceSplitEnumerator<SplitT> enumerator, Object splitAssignmentLock) {
        try {
            while (!cancellationRequested.get()) {
                Optional<SplitT> nextSplit;
                boolean finished;
                synchronized (splitAssignmentLock) {
                    nextSplit = enumerator.nextSplit();
                    finished = enumerator.isFinished();
                }
                if (nextSplit.isPresent()) {
                    runParallelSplit(nextSplit.get());
                    continue;
                }
                if (finished) return;
                Thread.sleep(IDLE_SLEEP_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!cancellationRequested.get()) {
                fail(e);
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            sourceWorkerThreads.remove(Thread.currentThread());
        }
    }

    private void runParallelSplit(SplitT split) throws Exception {
        try (SourceReader<SplitT> reader = source.createReader()) {
            reader.open(split);
            while (!cancellationRequested.get() && !reader.isFinished()) {
                List<YakRow> rows = reader.poll();
                if (!rows.isEmpty()) {
                    channel.put(new RowBatchMessage(rows));
                    readRows.addAndGet(rows.size());
                } else {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                }
            }
        }
    }

    private void runSplit(SourceSplitEnumerator<SplitT> enumerator, SplitT split) throws Exception {
        try (SourceReader<SplitT> reader = source.createReader()) {
            reader.open(split);
            while (!cancellationRequested.get() && !reader.isFinished()) {
                processCompletedCheckpoints(reader);
                maybeRequestAutomaticCheckpoint();

                List<YakRow> rows = reader.poll();
                if (!rows.isEmpty()) {
                    channel.put(new RowBatchMessage(rows));
                    readRows.addAndGet(rows.size());
                }

                processCheckpointRequests(enumerator, reader, split);
                processCompletedCheckpoints(reader);
                if (rows.isEmpty()) {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                }
            }
            processCheckpointRequests(enumerator, reader, split);
            processCompletedCheckpoints(reader);
        }
    }

    private void maybeRequestAutomaticCheckpoint() {
        if (source.boundedness() != Boundedness.CONTINUOUS_UNBOUNDED
                || autoCheckpointIntervalNanos <= 0
                || automaticCheckpointPending.get()) {
            return;
        }

        long now = System.nanoTime();
        if (now < nextAutoCheckpointNanos) {
            return;
        }

        long checkpointId = checkpointSequence.incrementAndGet();
        automaticCheckpointIds.add(checkpointId);
        automaticCheckpointPending.set(true);
        checkpointRequests.add(new CheckpointRequest(checkpointId, true));
        nextAutoCheckpointNanos = now + autoCheckpointIntervalNanos;
    }

    private void processCheckpointRequests(
            SourceSplitEnumerator<SplitT> enumerator, SourceReader<SplitT> reader, SplitT split) throws Exception {
        CheckpointRequest request;
        while ((request = checkpointRequests.poll()) != null) {
            CheckpointState enumeratorState = enumerator.snapshotState(request.checkpointId());
            CheckpointState readerState = reader == null ? null : reader.snapshotState(request.checkpointId());
            String splitId = split == null ? null : split.splitId();
            channel.put(new CheckpointBarrierMessage(request.checkpointId(), enumeratorState, readerState, splitId));
        }
    }

    private void processCompletedCheckpoints(SourceReader<SplitT> reader) throws Exception {
        LocalCheckpoint checkpoint;
        while ((checkpoint = completedCheckpoints.poll()) != null) {
            if (reader != null && checkpoint.readerState() != null) {
                reader.notifyCheckpointComplete(checkpoint.checkpointId());
            }
            latestCheckpoint = checkpoint;

            CompletableFuture<LocalCheckpoint> future = checkpointFutures.remove(checkpoint.checkpointId());
            if (future != null) {
                future.complete(checkpoint);
            }
            if (automaticCheckpointIds.remove(checkpoint.checkpointId())) {
                automaticCheckpointPending.set(false);
            }
        }
    }

    private void runSink() {
        try (SinkWriter writer = sink.createWriter(schema)) {
            writer.open();
            while (!cancellationRequested.get()) {
                ChannelMessage message = channel.take();
                if (message instanceof RowBatchMessage rowBatch) {
                    writer.write(rowBatch.rows());
                    writeRows.addAndGet(rowBatch.rows().size());
                    continue;
                }
                if (message instanceof CheckpointBarrierMessage barrier) {
                    writer.flush();
                    completeCheckpoint(barrier);
                    continue;
                }
                if (message == EndOfInputMessage.INSTANCE) {
                    writer.flush();
                    return;
                }
                throw new IllegalStateException(
                        "unsupported channel message: " + message.getClass().getName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!cancellationRequested.get()) {
                fail(e);
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            workerFinished();
        }
    }

    private void completeCheckpoint(CheckpointBarrierMessage barrier) {
        completedCheckpoints.add(new LocalCheckpoint(
                barrier.checkpointId(),
                barrier.enumeratorState(),
                barrier.readerState(),
                barrier.splitId(),
                System.currentTimeMillis()));
    }

    private void fail(Throwable cause) {
        if (!status.compareAndSet(ExecutionStatus.RUNNING, ExecutionStatus.FAILED)) {
            return;
        }
        failure = cause;
        cancellationRequested.set(true);
        interruptWorkers();
        completePendingCheckpoints(cause);
    }

    private void interruptWorkers() {
        Thread current = Thread.currentThread();
        Thread currentSourceThread = sourceThread;
        Thread currentSinkThread = sinkThread;
        if (currentSourceThread != null && currentSourceThread != current) {
            currentSourceThread.interrupt();
        }
        sourceWorkerThreads.forEach(thread -> {
            if (thread != current) {
                thread.interrupt();
            }
        });
        if (currentSinkThread != null && currentSinkThread != current) {
            currentSinkThread.interrupt();
        }
    }

    private void completePendingCheckpoints(Throwable cause) {
        checkpointFutures.forEach((checkpointId, future) -> {
            if (checkpointFutures.remove(checkpointId, future)) {
                future.completeExceptionally(cause);
            }
        });
    }

    private void workerFinished() {
        if (workersRemaining.decrementAndGet() != 0) {
            return;
        }
        if (status.compareAndSet(ExecutionStatus.RUNNING, ExecutionStatus.SUCCEEDED)) {
            completePendingCheckpoints(new IllegalStateException("execution completed before checkpoint"));
        }
        executionFinished.countDown();
    }
}
