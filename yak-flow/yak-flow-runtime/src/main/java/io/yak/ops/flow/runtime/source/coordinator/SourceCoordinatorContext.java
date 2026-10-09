package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.operators.coordination.SubtaskGateway;
import io.yak.ops.flow.runtime.source.event.AddSplitEvent;
import io.yak.ops.flow.runtime.source.event.NoMoreSplitsEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * SourceCoordinator 的分片协调上下文。
 *
 * <p>Enumerator 通过本类注册 Reader、投递 Split、异步发现分片。
 * 除 callAsync() 中的阻塞查询外，全部状态只能在协调器线程访问。
 * 本类不运行 SourceReader，也不处理数据记录。
 */
public final class SourceCoordinatorContext<SplitT extends SourceSplit>
        implements SplitEnumeratorContext<SplitT> {

    private final OperatorCoordinatorContext operatorContext;
    private final ExecutorService coordinatorExecutor;
    private final ExecutorService discoveryExecutor;
    private final Supplier<Thread> coordinatorThread;
    private final Consumer<Throwable> onFailure;
    private final SplitAssignmentTracker<SplitT> assignments = new SplitAssignmentTracker<>();
    private final Map<Integer, SubtaskGateway> readers = new HashMap<>();
    private final Map<Integer, RuntimeTaskInfo> readerIdentities = new HashMap<>();
    private final Map<String, Integer> inFlight = new HashMap<>();
    private final Set<Integer> noMoreRequested = new HashSet<>();
    private final Set<Integer> noMoreDispatched = new HashSet<>();
    private final Set<Integer> pendingNoMore = new HashSet<>();
    private final List<Runnable> postponedDiscoveryCallbacks = new ArrayList<>();
    private boolean checkpointPaused;
    private volatile boolean closed;

    SourceCoordinatorContext(OperatorCoordinatorContext operatorContext,
            ExecutorService coordinatorExecutor,
            ExecutorService discoveryExecutor,
            Supplier<Thread> coordinatorThread,
            Consumer<Throwable> onFailure) {
        this.operatorContext = Objects.requireNonNull(operatorContext, "operatorContext 不能为空");
        this.coordinatorExecutor = Objects.requireNonNull(coordinatorExecutor);
        this.discoveryExecutor = Objects.requireNonNull(discoveryExecutor);
        this.coordinatorThread = Objects.requireNonNull(coordinatorThread);
        this.onFailure = Objects.requireNonNull(onFailure);
    }

    void registerReader(RuntimeTaskInfo taskInfo, SubtaskGateway gateway) {
        assertCoordinatorThread();
        if (checkpointPaused) {
            throw new IllegalStateException("Checkpoint 对齐期间不允许注册新 Reader");
        }
        operatorContext.validateTask(taskInfo);
        int subtaskId = taskInfo.subtaskIndex();
        checkSubtask(subtaskId);
        Objects.requireNonNull(gateway, "gateway 不能为空");
        if (readers.putIfAbsent(subtaskId, gateway) != null) {
            throw new IllegalArgumentException("Reader 已注册，不支持单个 Reader 的 Attempt 热替换：" + subtaskId);
        }
        readerIdentities.put(subtaskId, taskInfo);
    }

    void checkRegistered(RuntimeTaskInfo taskInfo) {
        assertCoordinatorThread();
        operatorContext.validateTask(taskInfo);
        RuntimeTaskInfo active = readerIdentities.get(taskInfo.subtaskIndex());
        if (!taskInfo.equals(active)) {
            throw new IllegalStateException("Reader 未注册或已过期，subtask="
                    + taskInfo.subtaskIndex() + "，attempt=" + taskInfo.attemptNumber());
        }
    }

    boolean canRequestSplit(RuntimeTaskInfo taskInfo) {
        checkRegistered(taskInfo);
        return !noMoreRequested.contains(taskInfo.subtaskIndex());
    }

    private boolean canAssignSplit(int subtaskId) {
        checkSubtask(subtaskId);
        if (!readerIdentities.containsKey(subtaskId)) {
            throw new IllegalStateException("Reader 尚未注册：" + subtaskId);
        }
        return !noMoreRequested.contains(subtaskId);
    }

    void pauseForCheckpoint() {
        assertCoordinatorThread();
        if (checkpointPaused) {
            throw new IllegalStateException("Coordinator Context 已暂停");
        }
        checkpointPaused = true;
    }

    void resumeAfterCheckpoint() {
        assertCoordinatorThread();
        checkpointPaused = false;
        List<Runnable> deferred = List.copyOf(postponedDiscoveryCallbacks);
        postponedDiscoveryCallbacks.clear();
        for (Runnable callback : deferred) {
            callback.run();
        }
    }

    void ensureDeliveriesCompleted() {
        assertCoordinatorThread();
        if (!inFlight.isEmpty() || !pendingNoMore.isEmpty()) {
            throw new IllegalStateException("仍有尚未确认的分片/结束事件，不能快照 Enumerator");
        }
    }

    SplitAssignmentTracker<SplitT> assignmentTracker() {
        assertCoordinatorThread();
        return assignments;
    }

    void dispose() {
        assertCoordinatorThread();
        closed = true;
        readers.clear();
        readerIdentities.clear();
        postponedDiscoveryCallbacks.clear();
        inFlight.clear();
        pendingNoMore.clear();
        noMoreRequested.clear();
        noMoreDispatched.clear();
    }

    @Override
    public int currentParallelism() {
        return operatorContext.parallelism();
    }

    @Override
    public Set<Integer> registeredReaders() {
        assertCoordinatorThread();
        return Set.copyOf(readers.keySet());
    }

    @Override
    public void assignSplit(SplitT split, int subtaskId) {
        assertCoordinatorThread();
        if (!canAssignSplit(subtaskId)) {
            throw new IllegalStateException("不能在 NoMoreSplits 后继续分配 Split：" + subtaskId);
        }
        AddSplitEvent<SplitT> event = new AddSplitEvent<>(List.of(split));
        String splitId = split.splitId();
        if (inFlight.putIfAbsent(splitId, subtaskId) != null) {
            throw new IllegalArgumentException("重复交付中的 Split：" + splitId);
        }
        try {
            assignments.recordAssignment(subtaskId, split);
            CompletionStage<Void> delivered = Objects.requireNonNull(
                    readers.get(subtaskId).sendEvent(event), "SubtaskGateway 返回了 null");
            delivered.whenComplete((unused, error) -> post(() -> {
                if (error != null) {
                    onFailure.accept(new IllegalStateException("Split 交付失败：" + splitId, error));
                } else {
                    inFlight.remove(splitId);
                    maybeSendNoMore(subtaskId);
                }
            }));
        } catch (Throwable error) {
            onFailure.accept(error);
            throw error;
        }
    }

    @Override
    public void signalNoMoreSplits(int subtaskId) {
        assertCoordinatorThread();
        checkSubtask(subtaskId);
        if (!readers.containsKey(subtaskId)) {
            throw new IllegalArgumentException("Reader 尚未注册：" + subtaskId);
        }
        noMoreRequested.add(subtaskId);
        maybeSendNoMore(subtaskId);
    }

    private void maybeSendNoMore(int subtaskId) {
        if (!noMoreRequested.contains(subtaskId)
                || noMoreDispatched.contains(subtaskId)
                || inFlight.containsValue(subtaskId)) {
            return;
        }
        noMoreDispatched.add(subtaskId);
        pendingNoMore.add(subtaskId);
        try {
            CompletionStage<Void> delivered = Objects.requireNonNull(
                    readers.get(subtaskId).sendEvent(new NoMoreSplitsEvent()), "SubtaskGateway 返回了 null");
            delivered.whenComplete((unused, error) -> post(() -> {
                if (error != null) {
                    onFailure.accept(new IllegalStateException("NoMoreSplits 交付失败：" + subtaskId, error));
                } else {
                    pendingNoMore.remove(subtaskId);
                }
            }));
        } catch (Throwable error) {
            onFailure.accept(error);
            throw error;
        }
    }

    @Override
    public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
        assertCoordinatorThread();
        Objects.requireNonNull(action, "action 不能为空");
        Objects.requireNonNull(handler, "handler 不能为空");
        discoveryExecutor.execute(() -> {
            T value = null;
            Throwable error = null;
            try {
                value = action.call();
            } catch (Throwable failure) {
                error = failure;
            }
            T finishedValue = value;
            Throwable finishedError = error;
            post(() -> {
                Runnable callback = () -> handler.accept(finishedValue, finishedError);
                if (checkpointPaused) {
                    postponedDiscoveryCallbacks.add(callback);
                } else {
                    callback.run();
                }
            });
        });
    }

    @Override
    public void runInCoordinatorThread(Runnable action) {
        Objects.requireNonNull(action, "action 不能为空");
        Runnable guarded = () -> {
            if (checkpointPaused) {
                postponedDiscoveryCallbacks.add(action);
            } else {
                action.run();
            }
        };
        if (Thread.currentThread() == coordinatorThread.get()) {
            guarded.run();
        } else {
            post(guarded);
        }
    }

    private void post(Runnable action) {
        if (closed) {
            return;
        }
        try {
            coordinatorExecutor.execute(() -> {
                if (closed) {
                    return;
                }
                try {
                    action.run();
                } catch (Throwable failure) {
                    onFailure.accept(failure);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // 协调器已经关闭，迟到的异步回调不能改变任务状态。
        }
    }

    private void checkSubtask(int subtaskId) {
        if (subtaskId < 0 || subtaskId >= currentParallelism()) {
            throw new IllegalArgumentException("Reader 子任务编号越界：" + subtaskId);
        }
    }

    private void assertCoordinatorThread() {
        if (Thread.currentThread() != coordinatorThread.get()) {
            throw new IllegalStateException("SourceCoordinatorContext 只能在协调器线程使用");
        }
    }
}
