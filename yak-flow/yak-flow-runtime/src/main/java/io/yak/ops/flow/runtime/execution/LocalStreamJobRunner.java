package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.graph.StreamEdge;
import io.yak.ops.core.graph.StreamGraph;
import io.yak.ops.core.graph.StreamNode;
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
 * Core-based 本地 Job 执行器：仅装配一条 Source → OneInputOperator* → Sink 线性链。
 *
 * <p>PR5 在单条 Task Mailbox 线程中内联运行整条链；跨 Task 数据路由、不同并行度、
 * 全局 Checkpoint 与恢复由后续独立契约实现。每次调用独立创建 Coordinator、Task、
 * Operator 和 SinkWriter，不保存任何 Job 专属的可变状态。
 */
public final class LocalStreamJobRunner implements LocalJobRunner {

    @Override
    public void validate(CompiledJobPlan plan) {
        Objects.requireNonNull(plan, "plan 不能为空");
        StreamGraph graph = plan.graph();
        List<StreamNode> nodes = graph.getTopologicalNodes();
        if (graph.getSourceNodes().size() != 1 || graph.getSinkNodes().size() != 1 || nodes.size() < 2) {
            throw new UnsupportedOperationException("本地单 Task Runner 仅支持一个 Source 和一个 Sink");
        }
        for (int i = 0; i < nodes.size(); i++) {
            StreamNode node = nodes.get(i);
            if (node.getParallelism() != 1) {
                throw new UnsupportedOperationException(
                        "尚未支持并行度大于 1 的 Task/Channel 路由：" + node.getId());
            }
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
