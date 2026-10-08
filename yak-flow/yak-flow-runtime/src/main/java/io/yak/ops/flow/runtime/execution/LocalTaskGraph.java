package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.graph.StreamEdge;
import io.yak.ops.core.graph.StreamGraph;
import io.yak.ops.core.graph.StreamNode;
import io.yak.ops.flow.runtime.io.LocalChannel;
import io.yak.ops.flow.runtime.io.LocalResultPartition;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import io.yak.ops.flow.runtime.tasks.OneInputOperatorStreamTask;
import io.yak.ops.flow.runtime.tasks.SinkOperatorStreamTask;
import io.yak.ops.flow.runtime.tasks.SourceOperatorStreamTask;
import io.yak.ops.flow.runtime.tasks.StreamTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 单个 Core-based Job 的本地多 Task 装配与生命周期。
 *
 * <p>每条线性边在目标端为每个 Subtask 创建一个有界 Channel；每个算子子任务拥有独立
 * StreamTask 和 Operator/Writer，正常 EOF 沿图向下游传播。
 * 任意失败先中止全部 Channel 再取消其它 Task，避免上游阻塞在已停止的下游上。
 */
final class LocalTaskGraph {

    private final CompiledJobPlan plan;
    private final BooleanSupplier cancellationRequested;
    private final int channelCapacity;
    private final List<StreamTask> tasks = new ArrayList<>();
    private final List<LocalChannel<Object>> channels = new ArrayList<>();
    private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

    private SourceCoordinator<SourceSplit, Object> coordinator;

    LocalTaskGraph(CompiledJobPlan plan, BooleanSupplier cancellationRequested, int channelCapacity) {
        this.plan = Objects.requireNonNull(plan, "plan 不能为空");
        this.cancellationRequested = Objects.requireNonNull(cancellationRequested, "cancellationRequested 不能为空");
        if (channelCapacity <= 0) {
            throw new IllegalArgumentException("channelCapacity 必须为正整数");
        }
        this.channelCapacity = channelCapacity;
    }

    void run() throws Exception {
        Throwable outcome = null;
        try {
            assemble();
            for (StreamTask task : tasks) {
                task.completionFuture().whenComplete((unused, error) -> {
                    if (error != null) {
                        failJob(unwrap(error));
                    }
                });
            }
            coordinator.terminationFuture().whenComplete((unused, error) -> {
                if (error != null) {
                    failJob(unwrap(error));
                }
            });

            ensureNotCancelled();
            await(coordinator.start());
            // Tasks 已按 Sink → Operators → Source 的顺序装配；先启动消费者再启动生产者。
            for (StreamTask task : tasks) {
                ensureNotCancelled();
                await(task.start());
            }
            CompletableFuture<?>[] completion = tasks.stream()
                    .map(StreamTask::completionFuture)
                    .toArray(CompletableFuture<?>[]::new);
            try {
                await(CompletableFuture.allOf(completion));
            } catch (Exception | Error failure) {
                Throwable original = firstFailure.get();
                if (original != null) {
                    throwFailure(original);
                }
                throw failure;
            }
            Throwable failure = firstFailure.get();
            if (failure != null) {
                throwFailure(failure);
            }
        } catch (Exception | Error error) {
            Throwable original = firstFailure.get();
            outcome = original == null ? error : original;
            stopAll(outcome);
        } finally {
            Throwable closeFailure = closeAll();
            if (closeFailure != null) {
                if (outcome == null) {
                    outcome = closeFailure;
                } else if (outcome != closeFailure) {
                    outcome.addSuppressed(closeFailure);
                }
            }
        }
        if (outcome != null) {
            throwFailure(outcome);
        }
    }

    private void assemble() {
        StreamGraph graph = plan.graph();
        List<StreamNode> nodes = graph.getTopologicalNodes();
        List<List<LocalChannel<Object>>> inputs = new ArrayList<>();
        for (int index = 1; index < nodes.size(); index++) {
            StreamNode previous = nodes.get(index - 1);
            StreamNode target = nodes.get(index);
            List<LocalChannel<Object>> stageInputs = new ArrayList<>();
            for (int subtask = 0; subtask < target.getParallelism(); subtask++) {
                LocalChannel<Object> channel = new LocalChannel<>(channelCapacity, previous.getParallelism());
                stageInputs.add(channel);
                channels.add(channel);
            }
            inputs.add(List.copyOf(stageInputs));
        }

        // 按拓扑逆序装配下游，任务中的 Connector 实例仍延迟到 openTask() 创建。
        for (int index = nodes.size() - 1; index >= 1; index--) {
            StreamNode node = nodes.get(index);
            List<LocalChannel<Object>> stageInputs = inputs.get(index - 1);
            for (int subtask = 0; subtask < node.getParallelism(); subtask++) {
                TaskEnvironment environment = environment(node, subtask);
                if (node.isSink()) {
                    tasks.add(new SinkOperatorStreamTask(node, environment, stageInputs.get(subtask)));
                } else {
                    StreamEdge downstreamEdge = graph.getOutEdges(node.getId()).getFirst();
                    LocalResultPartition<Object> partition = new LocalResultPartition<>(
                            downstreamEdge, subtask, node.getParallelism(), inputs.get(index));
                    tasks.add(new OneInputOperatorStreamTask(
                            node, environment, stageInputs.get(subtask), partition));
                }
            }
        }

        StreamNode sourceNode = nodes.getFirst();
        Source<Object, SourceSplit, Object> source = castSource(sourceNode);
        OperatorCoordinatorContext coordinatorContext = new OperatorCoordinatorContext(
                plan.jobID(), sourceNode.getId(), sourceNode.getParallelism());
        coordinator = new SourceCoordinator<>(source, coordinatorContext);
        StreamEdge outgoing = graph.getOutEdges(sourceNode.getId()).getFirst();

        for (int subtask = 0; subtask < sourceNode.getParallelism(); subtask++) {
            LocalResultPartition<Object> partition = new LocalResultPartition<>(
                    outgoing, subtask, sourceNode.getParallelism(), inputs.getFirst());
            tasks.add(new SourceOperatorStreamTask<>(
                    source, coordinator, environment(sourceNode, subtask), partition));
        }
    }

    private TaskEnvironment environment(StreamNode node, int subtask) {
        return new TaskEnvironment(
                new TaskInfo(plan.jobID(), node.getId(), subtask, node.getParallelism(), 0),
                plan.configuration());
    }

    private void ensureNotCancelled() {
        if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("本地 Job 已请求取消");
        }
    }

    private void failJob(Throwable cause) {
        if (firstFailure.compareAndSet(null, Objects.requireNonNull(cause, "cause 不能为空"))) {
            stopAll(cause);
        }
    }

    private void stopAll(Throwable cause) {
        for (LocalChannel<Object> channel : channels) {
            channel.abort(cause);
        }
        for (StreamTask task : tasks) {
            // Task 可能已结束或尚未启动；取消请求仅用于唤醒与资源收敛。
            task.cancelAsync();
        }
    }

    private Throwable closeAll() {
        Throwable failure = null;
        for (StreamTask task : tasks) {
            try {
                task.close();
            } catch (Throwable error) {
                failure = accumulate(failure, error);
            }
        }
        if (coordinator != null) {
            try {
                coordinator.close();
            } catch (Throwable error) {
                failure = accumulate(failure, error);
            }
        }
        return failure;
    }

    private static Throwable accumulate(Throwable previous, Throwable current) {
        if (previous == null) {
            return current;
        }
        if (previous != current) {
            previous.addSuppressed(current);
        }
        return previous;
    }

    @SuppressWarnings("unchecked")
    private static Source<Object, SourceSplit, Object> castSource(StreamNode node) {
        return (Source<Object, SourceSplit, Object>) node.getSource().orElseThrow();
    }

    private static void await(CompletableFuture<?> future) throws Exception {
        try {
            future.get();
        } catch (ExecutionException error) {
            throwFailure(unwrap(error));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable actual = failure;
        while ((actual instanceof ExecutionException || actual instanceof CompletionException)
                && actual.getCause() != null) {
            actual = actual.getCause();
        }
        return actual;
    }

    private static void throwFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(failure);
    }
}
