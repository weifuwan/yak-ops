package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.graph.StreamEdge;
import io.yak.ops.core.graph.StreamGraph;
import io.yak.ops.core.graph.StreamNode;
import io.yak.ops.core.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.operators.LocalOperatorChain;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import io.yak.ops.flow.runtime.tasks.SourceOperatorStreamTask;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

/**
 * Core-based 本地 Job 执行器：支持一个 Source → OneInputOperator* → Sink 的线性图。
 *
 * <p>并行度全为 1 且 FORWARD 的图沿用单 Mailbox 内联链；其它合法并行线性图由
 * LocalTaskGraph 建立独立 Operator/Sink Task、显式分区以及有界 Channel。
 * 不支持多源、分叉、网络 Shuffle 或尚未完成的全局 Checkpoint。
 */
public final class LocalStreamJobRunner implements LocalJobRunner {

    private static final int MAX_PARALLELISM = 16;
    private static final int MAX_JOB_SUBTASKS = 64;
    private static final int MAX_CHANNEL_CAPACITY = 4096;

    @Override
    public void validate(CompiledJobPlan plan) {
        Objects.requireNonNull(plan, "plan 不能为空");
        StreamGraph graph = plan.graph();
        List<StreamNode> nodes = graph.getTopologicalNodes();
        if (graph.getSourceNodes().size() != 1 || graph.getSinkNodes().size() != 1 || nodes.size() < 2) {
            throw new UnsupportedOperationException("本地单 Task Runner 仅支持一个 Source 和一个 Sink");
        }
        int totalTasks = 0;
        for (int i = 0; i < nodes.size(); i++) {
            StreamNode node = nodes.get(i);
            if (node.getParallelism() <= 0 || node.getParallelism() > MAX_PARALLELISM) {
                throw new UnsupportedOperationException(
                        "单个本地算子的并行度必须在 1 到 " + MAX_PARALLELISM + " 之间：" + node.getId());
            }
            totalTasks += node.getParallelism();
            if (i == 0) {
                if (!node.isSource() || !graph.getInEdges(node.getId()).isEmpty()) {
                    throw new UnsupportedOperationException("线性链的首节点必须为 Source");
                }
            } else {
                List<StreamEdge> incoming = graph.getInEdges(node.getId());
                if (incoming.size() != 1 || incoming.getFirst().sourceId() != nodes.get(i - 1).getId()) {
                    throw new UnsupportedOperationException("目前不支持多输入、分叉或跨 Task 数据路由");
                }
            }
            if (i == nodes.size() - 1) {
                if (!node.isSink() || !graph.getOutEdges(node.getId()).isEmpty()) {
                    throw new UnsupportedOperationException("线性链的末节点必须为 Sink");
                }
            } else {
                List<StreamEdge> outgoing = graph.getOutEdges(node.getId());
                if (outgoing.size() != 1 || outgoing.getFirst().targetId() != nodes.get(i + 1).getId()) {
                    throw new UnsupportedOperationException("目前不支持多 Sink 或分叉数据路由");
                }
                if (i > 0 && !node.isOperator()) {
                    throw new UnsupportedOperationException("线性链中间只允许单输入 Operator");
                }
            }
        }
        if (totalTasks > MAX_JOB_SUBTASKS) {
            throw new UnsupportedOperationException("单个本地 Job 子任务数量上限为 " + MAX_JOB_SUBTASKS);
        }
        int capacity = plan.configuration().get(CoreOptions.LOCAL_CHANNEL_CAPACITY);
        if (capacity <= 0 || capacity > MAX_CHANNEL_CAPACITY) {
            throw new IllegalArgumentException("execution.local-channel.capacity 必须在 1 到 "
                    + MAX_CHANNEL_CAPACITY + " 之间");
        }
        Duration interval = plan.configuration().get(CheckpointingOptions.CHECKPOINTING_INTERVAL);
        if (!interval.isZero()) {
            throw new UnsupportedOperationException("新 Runtime 尚未实现全局 Checkpoint，不能开启周期 Checkpoint");
        }
    }

    @Override
    public void run(CompiledJobPlan plan, BooleanSupplier cancellationRequested) throws Exception {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested 不能为空");
        validate(plan);
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("作业已请求取消");
        }

        if (!canRunInline(plan.graph())) {
            new LocalTaskGraph(plan, cancellationRequested,
                    plan.configuration().get(CoreOptions.LOCAL_CHANNEL_CAPACITY)).run();
            return;
        }

        runInline(plan, cancellationRequested);
    }

    private static boolean canRunInline(StreamGraph graph) {
        return graph.getStreamNodes().stream().allMatch(node -> node.getParallelism() == 1)
                && graph.getStreamEdges().stream()
                        .allMatch(edge -> edge.partitioning() == StreamPartitioning.FORWARD);
    }

    /** 兼容已有单并行 Task 语义：Reader、Operator Chain、SinkWriter 共用同一 Mailbox。 */
    private static void runInline(CompiledJobPlan plan, BooleanSupplier cancellationRequested) throws Exception {
        List<StreamNode> nodes = plan.graph().getTopologicalNodes();
        StreamNode sourceNode = nodes.getFirst();
        StreamNode sinkNode = nodes.getLast();
        LocalOperatorChain chain = new LocalOperatorChain(nodes.subList(1, nodes.size() - 1), sinkNode);
        Source<Object, SourceSplit, Object> source = castSource(sourceNode);
        TaskInfo taskInfo = new TaskInfo(plan.jobID(), sourceNode.getId(), 0, 1, 0);
        OperatorCoordinatorContext context = new OperatorCoordinatorContext(
                plan.jobID(), sourceNode.getId(), sourceNode.getParallelism());

        try (SourceCoordinator<SourceSplit, Object> coordinator = new SourceCoordinator<>(source, context)) {
            TaskEnvironment environment = new TaskEnvironment(taskInfo, plan.configuration());
            try (SourceOperatorStreamTask<Object, SourceSplit> task = new SourceOperatorStreamTask<>(
                    source, coordinator, environment, chain, chain)) {
                coordinator.terminationFuture().whenComplete((unused, failure) -> {
                    if (failure != null) {
                        task.coordinatorFailed(failure);
                    }
                });

                await(coordinator.start());
                await(task.start());
                await(task.completionFuture());
            }
        }
    }

    /**
     * 擦除图节点的泛型仅发生在 Runner 装配边界；StreamGraph 已校验相邻节点的输出/输入类型。
     */
    @SuppressWarnings("unchecked")
    private static Source<Object, SourceSplit, Object> castSource(StreamNode node) {
        return (Source<Object, SourceSplit, Object>) node.getSource().orElseThrow();
    }

    private static void await(java.util.concurrent.CompletableFuture<Void> future) throws Exception {
        try {
            future.get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }
}
