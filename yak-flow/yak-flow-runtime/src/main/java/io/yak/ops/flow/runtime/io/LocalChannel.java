package io.yak.ops.flow.runtime.io;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个下游 Subtask 的有界本地输入队列。
 *
 * <p>可被多个上游 Task 并发写入，由一个下游 Task Mailbox 串行消费；producerFinished
 * 在该生产者全部数据成功放入队列后调用。只有全部上游结束且队列排空才返回 END_OF_INPUT。
 * 当下游停止时，abort 让阻塞的生产者在有限时间内感知失败。
 */
public final class LocalChannel<T> {

    private static final long BACKPRESSURE_POLL_MILLIS = 25;

    private final ArrayBlockingQueue<T> records;
    private final boolean[] finishedProducers;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final Object stateLock = new Object();

    private int remainingProducers;
    private CompletableFuture<Void> available = new CompletableFuture<>();
    private CompletableFuture<Void> drained = CompletableFuture.completedFuture(null);
    private int processingRecords;

    public LocalChannel(int capacity, int producerCount) {
        if (capacity <= 0 || producerCount <= 0) {
            throw new IllegalArgumentException("Channel 容量和生产者数量必须为正整数");
        }
        this.records = new ArrayBlockingQueue<>(capacity);
        this.finishedProducers = new boolean[producerCount];
        this.remainingProducers = producerCount;
    }

    /** 队列已满时阻塞当前虚拟线程；不能用无限缓存绕过背压。 */
    public void send(T value) throws Exception {
        Objects.requireNonNull(value, "数据记录不能为空");
        while (true) {
            checkFailure();
            if (records.offer(value, BACKPRESSURE_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                synchronized (stateLock) {
                    // 处理中的记录也是未完成的数据，不能让 Checkpoint 提前越过它。
                    if (!records.isEmpty() || processingRecords > 0) {
                        if (drained.isDone()) {
                            drained = new CompletableFuture<>();
                        }
                    }
                    if (!records.isEmpty()) {
                        available.complete(null);
                    }
                }
                checkFailure();
                return;
            }
        }
    }

    /** 下游 Task Mailbox 的一次非阻塞读取；不会在此方法阻塞等待数据。 */
    public InputStatus emitNext(ReaderOutput<T> output) throws Exception {
        Objects.requireNonNull(output, "output 不能为空");
        checkFailure();
        T record;
        synchronized (stateLock) {
            record = records.poll();
            if (record != null) {
                processingRecords++;
                if (drained.isDone()) {
                    drained = new CompletableFuture<>();
                }
            } else {
                checkFailure();
                if (!records.isEmpty()) {
                    return InputStatus.MORE_AVAILABLE;
                }
                return remainingProducers == 0 ? InputStatus.END_OF_INPUT : InputStatus.NOTHING_AVAILABLE;
            }
        }
        try {
            output.collect(record);
            return InputStatus.MORE_AVAILABLE;
        } finally {
            synchronized (stateLock) {
                processingRecords--;
                if (records.isEmpty() && processingRecords == 0) {
                    drained.complete(null);
                }
            }
        }
    }

    /** 与 emitNext() 配合使用，避免空队列时自旋；新数据或结束都将唤醒 Task。 */
    public CompletableFuture<Void> isAvailable() {
        synchronized (stateLock) {
            Throwable error = failure.get();
            if (error != null) {
                return CompletableFuture.failedFuture(error);
            }
            if (!records.isEmpty() || remainingProducers == 0) {
                return CompletableFuture.completedFuture(null);
            }
            if (available.isDone()) {
                available = new CompletableFuture<>();
            }
            return available.copy();
        }
    }

    /** 所有在队列中的记录及正在执行的下游回调均完成后的快照屏障。 */
    public CompletableFuture<Void> drainedFuture() {
        synchronized (stateLock) {
            Throwable error = failure.get();
            if (error != null) {
                return CompletableFuture.failedFuture(error);
            }
            if (records.isEmpty() && processingRecords == 0) {
                return CompletableFuture.completedFuture(null);
            }
            return drained.copy();
        }
    }

    /** 当前生产者完成自己的全部数据写入后，在每个下游 Channel 上恰好调用一次。 */
    public void producerFinished(int producerIndex) {
        synchronized (stateLock) {
            if (producerIndex < 0 || producerIndex >= finishedProducers.length) {
                throw new IllegalArgumentException("未知的 Channel 生产者：" + producerIndex);
            }
            if (finishedProducers[producerIndex]) {
                throw new IllegalStateException("Channel 生产者重复结束：" + producerIndex);
            }
            finishedProducers[producerIndex] = true;
            if (--remainingProducers == 0) {
                available.complete(null);
            }
        }
    }

    /** 失败或取消时终止当前 Channel，唤醒下游并让上游阻塞 send 尽快返回。 */
    public void abort(Throwable cause) {
        Objects.requireNonNull(cause, "cause 不能为空");
        if (failure.compareAndSet(null, cause)) {
            synchronized (stateLock) {
                available.completeExceptionally(cause);
                drained.completeExceptionally(cause);
            }
        }
    }

    /** 返回当前排队的数据条数，主要用于背压诊断和单元测试。 */
    public int queuedRecords() {
        return records.size();
    }

    private void checkFailure() throws Exception {
        Throwable error = failure.get();
        if (error == null) {
            return;
        }
        if (error instanceof Exception exception) {
            throw exception;
        }
        if (error instanceof Error serious) {
            throw serious;
        }
        throw new IllegalStateException(error);
    }
}
