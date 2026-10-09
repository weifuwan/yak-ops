package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.FileCheckpointStore;
import io.yak.ops.flow.runtime.checkpoint.QuiescentCheckpointCoordinator;
import io.yak.ops.flow.runtime.configuration.RuntimeOptions;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.io.RecordChannel;
import io.yak.ops.flow.runtime.io.RecordRouter;
import io.yak.ops.flow.runtime.jobgraph.JobEdge;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.jobgraph.JobVertex;
import io.yak.ops.flow.runtime.operators.OperatorChain;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import io.yak.ops.flow.runtime.tasks.OneInputStreamTask;
import io.yak.ops.flow.runtime.tasks.SinkOperatorStreamTask;
import io.yak.ops.flow.runtime.tasks.SourceOperatorStreamTask;
import io.yak.ops.flow.runtime.tasks.StreamTask;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Physical deployment of a JobGraph into local StreamTasks, their channels and the SourceCoordinator.
 * ExecutionGraph owns status and attempts; this class owns assembly and deterministic resource cleanup.
 */
final class TaskDeployment {

    private final ExecutionGraph executionGraph;
    private final JobGraph jobGraph;
    private final int channelCapacity;
    private final List<Execution> executions = new ArrayList<>();
    private final List<RecordChannel<Object>> channels = new ArrayList<>();
    private final List<List<RecordChannel<Object>>> channelStages = new ArrayList<>();
    private final List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks = new ArrayList<>();
    private final List<SinkOperatorStreamTask> sinkTasks = new ArrayList<>();
    private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

    private SourceCoordinator<SourceSplit, Object> coordinator;
    private CheckpointSnapshot restoredCheckpoint;
    private FileCheckpointStore checkpointStore;
    private QuiescentCheckpointCoordinator checkpointCoordinator;

    TaskDeployment(ExecutionGraph executionGraph) {
        this.executionGraph = Objects.requireNonNull(executionGraph, "executionGraph");
        this.jobGraph = executionGraph.getJobGraph();
        this.channelCapacity = jobGraph.configuration().get(RuntimeOptions.CHANNEL_CAPACITY);
        if (channelCapacity <= 0) {
            throw new IllegalArgumentException("Channel capacity must be positive");
        }
    }

    void deployAndAwait() throws Exception {
        Throwable outcome = null;
        try {
            assemble();
            if (checkpointCoordinator != null) {
                executionGraph.registerCheckpoint(checkpointCoordinator);
            }
            for (Execution execution : executions) {
                execution.completionFuture().whenComplete((unused, error) -> {
                    if (error != null) {
                        failJob(unwrap(error));
                    }
                });
            }
            if (!jobGraph.isSingleChainedVertex()) {
                coordinator.terminationFuture().whenComplete((unused, error) -> {
                    if (error != null) {
                        failJob(unwrap(error));
                    }
                });
            }

            ensureNotCancelled();
            await(coordinator.start());
            // Physical vertices are deployed in reverse order: consumers before their producers.
            for (Execution execution : executions) {
                ensureNotCancelled();
                await(execution.start());
            }
            if (checkpointCoordinator != null) {
                checkpointCoordinator.start();
            }
            try {
                await(CompletableFuture.allOf(executions.stream()
                        .map(Execution::completionFuture)
                        .toArray(CompletableFuture<?>[]::new)));
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
        } catch (Exception | Error failure) {
            Throwable original = firstFailure.get();
            outcome = original == null ? failure : original;
            stopAll(outcome);
        } finally {
            Throwable cleanupFailure = closeAll();
            if (cleanupFailure != null) {
                if (outcome == null) {
                    outcome = cleanupFailure;
                } else if (outcome != cleanupFailure) {
                    outcome.addSuppressed(cleanupFailure);
                }
            }
        }
        if (outcome != null) {
            throwFailure(outcome);
        }
    }

    private void assemble() throws Exception {
        if (jobGraph.isSingleChainedVertex()) {
            assembleChainedVertex();
            return;
        }

        if (checkpointEnabled()) {
            String directory = jobGraph.configuration().get(CheckpointingOptions.STATE_DIRECTORY);
            checkpointStore = new FileCheckpointStore(Path.of(directory));
            if (jobGraph.configuration().get(CheckpointingOptions.RESTORE_LATEST)) {
                restoredCheckpoint = checkpointStore.loadLatest(
                                FileCheckpointStore.graphSignature(jobGraph.graph()))
                        .orElseThrow(() -> new IllegalStateException("状态目录没有可恢复的完整 Checkpoint"));
            }
        }

        List<JobVertex> vertices = jobGraph.getVertices();
        List<JobEdge> edges = jobGraph.getEdges();
        List<List<RecordChannel<Object>>> inputs = new ArrayList<>();
        for (JobEdge edge : edges) {
            JobVertex previous = vertex(edge.sourceVertexId());
            JobVertex target = vertex(edge.targetVertexId());
            List<RecordChannel<Object>> inputChannels = new ArrayList<>();
            for (int subtask = 0; subtask < target.getParallelism(); subtask++) {
                RecordChannel<Object> channel = new RecordChannel<>(channelCapacity, previous.getParallelism());
                inputChannels.add(channel);
                channels.add(channel);
            }
            inputs.add(List.copyOf(inputChannels));
            channelStages.add(List.copyOf(inputChannels));
        }

        for (int index = vertices.size() - 1; index >= 1; index--) {
            JobVertex vertex = vertices.get(index);
            StreamNode node = vertex.getHeadOperator();
            List<RecordChannel<Object>> stageInputs = inputs.get(index - 1);
            for (int subtask = 0; subtask < vertex.getParallelism(); subtask++) {
                Execution execution = executionGraph.currentExecution(vertex.getId(), subtask);
                TaskEnvironment environment = environment(execution);
                StreamTask task;
                if (node.isSink()) {
                    SinkOperatorStreamTask sink = new SinkOperatorStreamTask(
                            node, environment, stageInputs.get(subtask));
                    sinkTasks.add(sink);
                    task = sink;
                } else {
                    JobEdge outputEdge = edges.get(index);
                    RecordRouter<Object> output = new RecordRouter<>(
                            outputEdge.streamEdge(), subtask, vertex.getParallelism(), inputs.get(index));
                    task = new OneInputStreamTask(node, environment, stageInputs.get(subtask), output);
                }
                bind(execution, task);
            }
        }

        JobVertex sourceVertex = vertices.getFirst();
        StreamNode sourceNode = sourceVertex.getHeadOperator();
        Source<Object, SourceSplit, Object> source = castSource(sourceNode);
        OperatorCoordinatorContext coordinatorContext = new OperatorCoordinatorContext(
                jobGraph.jobID(), sourceVertex.getId(), sourceVertex.getParallelism());
        Map<Integer, List<SourceSplit>> restoredReaderSplits = Map.of();
        if (restoredCheckpoint != null) {
            restoredReaderSplits = QuiescentCheckpointCoordinator.restoreSplits(restoredCheckpoint, source);
            Object enumeratorState = QuiescentCheckpointCoordinator.restoreEnumerator(restoredCheckpoint, source);
            coordinator = SourceCoordinator.restore(source, coordinatorContext, enumeratorState);
        } else {
            coordinator = new SourceCoordinator<>(source, coordinatorContext);
        }

        JobEdge firstEdge = edges.getFirst();
        for (int subtask = 0; subtask < sourceVertex.getParallelism(); subtask++) {
            Execution execution = executionGraph.currentExecution(sourceVertex.getId(), subtask);
            RecordRouter<Object> output = new RecordRouter<>(
                    firstEdge.streamEdge(), subtask, sourceVertex.getParallelism(), inputs.getFirst());
            SourceOperatorStreamTask<Object, SourceSplit> task = new SourceOperatorStreamTask<>(
                    source, coordinator, environment(execution), output,
                    null, restoredReaderSplits.getOrDefault(subtask, List.of()));
            sourceTasks.add(task);
            bind(execution, task);
        }

        if (checkpointStore != null) {
            checkpointCoordinator = new QuiescentCheckpointCoordinator(
                    jobGraph, source, coordinator, sourceTasks, channelStages, sinkTasks,
                    checkpointStore, restoredCheckpoint, executionGraph::isCancellationRequested, this::failJob);
        }
    }

    private void assembleChainedVertex() throws Exception {
        JobVertex vertex = jobGraph.getVertices().getFirst();
        List<StreamNode> operators = vertex.getOperators();
        StreamNode sourceNode = operators.getFirst();
        StreamNode sinkNode = operators.getLast();
        Source<Object, SourceSplit, Object> source = castSource(sourceNode);
        OperatorCoordinatorContext context = new OperatorCoordinatorContext(
                jobGraph.jobID(), vertex.getId(), vertex.getParallelism());
        coordinator = new SourceCoordinator<>(source, context);
        OperatorChain chain = new OperatorChain(operators.subList(1, operators.size() - 1), sinkNode);
        Execution execution = executionGraph.currentExecution(vertex.getId(), 0);
        SourceOperatorStreamTask<Object, SourceSplit> task = new SourceOperatorStreamTask<>(
                source, coordinator, environment(execution), chain, chain);
        sourceTasks.add(task);
        bind(execution, task);
        // For a chained Source/Operator/Sink, preserve the StreamTask's originating failure.
        // Coordinator failures are delivered through the Task mailbox, as in the original inline path.
        coordinator.terminationFuture().whenComplete((unused, error) -> {
            if (error != null) {
                task.coordinatorFailed(unwrap(error));
            }
        });
    }

    private void bind(Execution execution, StreamTask task) {
        execution.deploy(task);
        executions.add(execution);
    }

    private JobVertex vertex(int jobVertexId) {
        return jobGraph.getVertices().stream()
                .filter(vertex -> vertex.getId() == jobVertexId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown JobVertex id: " + jobVertexId));
    }

    private TaskEnvironment environment(Execution execution) {
        ExecutionVertex vertex = execution.getVertex();
        return new TaskEnvironment(vertex.taskInfo(jobGraph.jobID()), jobGraph.configuration());
    }

    private boolean checkpointEnabled() {
        return !jobGraph.configuration().get(CheckpointingOptions.CHECKPOINTING_INTERVAL).isZero()
                || jobGraph.configuration().get(CheckpointingOptions.RESTORE_LATEST);
    }

    private void ensureNotCancelled() {
        if (executionGraph.isCancellationRequested() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("本地 Job 已请求取消");
        }
    }

    private void failJob(Throwable cause) {
        if (firstFailure.compareAndSet(null, Objects.requireNonNull(cause, "cause 不能为空"))) {
            stopAll(cause);
        }
    }

    private void stopAll(Throwable cause) {
        for (RecordChannel<Object> channel : channels) {
            channel.abort(cause);
        }
        for (Execution execution : executions) {
            execution.cancelAsync();
        }
    }

    private Throwable closeAll() {
        Throwable failure = null;
        if (checkpointCoordinator != null) {
            try {
                checkpointCoordinator.close();
            } catch (Throwable error) {
                failure = accumulate(failure, error);
            }
        }
        for (Execution execution : executions) {
            try {
                execution.close();
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
        if (checkpointStore != null) {
            try {
                checkpointStore.close();
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
