package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import io.yak.ops.flow.runtime.checkpoint.CheckpointCoordinator;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
import io.yak.ops.flow.runtime.configuration.RuntimeOptions;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import io.yak.ops.flow.runtime.jobgraph.JobEdge;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.jobgraph.JobVertex;
import io.yak.ops.flow.runtime.operators.OperatorChain;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.operators.sink.SinkWriterOperator;
import io.yak.ops.flow.runtime.schema.SchemaCoordinator;
import io.yak.ops.flow.runtime.schema.SchemaManager;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
import io.yak.ops.flow.runtime.tasks.FinishedOnRestoreStreamTask;
import io.yak.ops.flow.runtime.tasks.OneInputStreamTask;
import io.yak.ops.flow.runtime.tasks.SourceOperatorStreamTask;
import io.yak.ops.flow.runtime.tasks.StreamTask;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Physical deployment of a JobGraph into local StreamTasks, producer ResultPartitions,
 * consumer InputGates and the SourceCoordinator.
 * ExecutionGraph owns status and attempts; this class owns assembly and deterministic resource cleanup.
 */
final class TaskDeployment {

    private final ExecutionGraph executionGraph;
    private final JobGraph jobGraph;
    private final int channelCapacity;
    private final boolean recoverFromLatestCheckpoint;
    private final List<Execution> executions = new ArrayList<>();
    private final List<InputGate<Object>> inputGates = new ArrayList<>();
    private final List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks = new ArrayList<>();
    private final List<OneInputStreamTask> inputTasks = new ArrayList<>();
    private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

    private SourceCoordinator<SourceSplit, Object> coordinator;
    private CheckpointSnapshot restoredCheckpoint;
    private LocalCompletedCheckpointStore checkpointStore;
    private CheckpointCoordinator checkpointCoordinator;
    private SchemaCoordinator schemaCoordinator;

    TaskDeployment(ExecutionGraph executionGraph, boolean recoverFromLatestCheckpoint) {
        this.executionGraph = Objects.requireNonNull(executionGraph, "executionGraph");
        this.recoverFromLatestCheckpoint = recoverFromLatestCheckpoint;
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
            if (coordinator != null && !jobGraph.isSingleChainedVertex()) {
                coordinator.terminationFuture().whenComplete((unused, error) -> {
                    if (error != null) {
                        failJob(unwrap(error));
                    }
                });
            }

            ensureNotCancelled();
            if (coordinator != null) {
                await(coordinator.start());
            }
            // Physical vertices are deployed in reverse order: consumers before their producers.
            for (Execution execution : executions) {
                ensureNotCancelled();
                await(execution.start());
            }
            if (checkpointCoordinator != null) {
                checkpointCoordinator.start();
            }
            try {
                await(CompletableFuture.allOf(
                        executions.stream().map(Execution::completionFuture).toArray(CompletableFuture<?>[]::new)));
            } catch (Exception | Error failure) {
                Throwable original = firstFailure.get();
                if (original != null) {
                    throwFailure(original);
                }
                throw failure;
            }
            if (checkpointCoordinator != null) {
                await(checkpointCoordinator.awaitPendingCheckpoints());
            }
            if (coordinator != null) {
                await(coordinator.awaitSourceEventDeliveries());
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
                outcome = accumulate(outcome, cleanupFailure);
            }
            if ((outcome != null || executionGraph.isCancellationRequested())
                    && cleanupFailure == null
                    && checkpointEnabled()
                    && !jobGraph.isSingleChainedVertex()) {
                try {
                    reconcileSinkCommits();
                } catch (Throwable recoveryFailure) {
                    outcome = accumulate(outcome, recoveryFailure);
                }
            }
        }
        if (outcome != null) {
            throwFailure(outcome);
        }
    }

    private void assemble() throws Exception {
        if (jobGraph.isSingleChainedVertex()) {
            if (jobGraph.configuration().get(RuntimeOptions.SCHEMA_EVOLUTION_ENABLED)) {
                throw new UnsupportedOperationException(
                        "Runtime schema evolution requires an unchained, checkpointed pipeline");
            }
            assembleChainedVertex();
            return;
        }

        if (checkpointEnabled()) {
            String directory = jobGraph.configuration().get(CheckpointingOptions.STATE_DIRECTORY);
            checkpointStore = new LocalCompletedCheckpointStore(Path.of(directory));
            if (recoverFromLatestCheckpoint || jobGraph.configuration().get(CheckpointingOptions.RESTORE_LATEST)) {
                restoredCheckpoint = checkpointStore
                        .loadLatest(LocalCompletedCheckpointStore.graphSignature(
                                jobGraph.graph(),
                                jobGraph.configuration().get(PipelineOptions.MAX_PARALLELISM),
                                jobGraph.configuration().get(CheckpointingOptions.DEFINITION_IDENTITY)))
                        .orElseThrow(() -> new IllegalStateException(
                                "No complete recoverable checkpoint exists in the state directory"));
                if (restoredCheckpoint.hasFinishedOperators()) {
                    validateFinishedRestore(restoredCheckpoint);
                }
            }
        }

        if (restoredCheckpoint != null
                && restoredCheckpoint.schemaManagerState() != null
                && !jobGraph.configuration().get(RuntimeOptions.SCHEMA_EVOLUTION_ENABLED)) {
            throw new IllegalStateException(
                    "Cannot restore a schema-evolved checkpoint with runtime schema evolution disabled");
        }

        List<JobVertex> vertices = jobGraph.getVertices();
        List<JobEdge> edges = jobGraph.getEdges();
        List<List<InputGate<Object>>> inputs = new ArrayList<>();
        for (JobEdge edge : edges) {
            JobVertex previous = vertex(edge.sourceVertexId());
            JobVertex target = vertex(edge.targetVertexId());
            List<InputGate<Object>> stageGates = new ArrayList<>();
            for (int subtask = 0; subtask < target.getParallelism(); subtask++) {
                InputGate<Object> inputGate = new InputGate<>(channelCapacity, previous.getParallelism());
                stageGates.add(inputGate);
                inputGates.add(inputGate);
            }
            inputs.add(List.copyOf(stageGates));
        }

        if (restoredCheckpoint != null && restoredCheckpoint.hasFinishedOperators()) {
            assembleFinishedOnRestore(vertices, edges, inputs);
            return;
        }

        for (int index = vertices.size() - 1; index >= 1; index--) {
            JobVertex vertex = vertices.get(index);
            StreamNode node = vertex.getHeadOperator();
            List<InputGate<Object>> stageInputs = inputs.get(index - 1);
            for (int subtask = 0; subtask < vertex.getParallelism(); subtask++) {
                Execution execution = executionGraph.currentExecution(vertex.getId(), subtask);
                TaskEnvironment environment = environment(execution);
                RecordWriterOutput<Object> output = null;
                if (!node.isSink()) {
                    JobEdge outputEdge = edges.get(index);
                    output = new RecordWriterOutput<>(
                            outputEdge.streamEdge(),
                            subtask,
                            vertex.getParallelism(),
                            new ResultPartition<>(subtask, inputs.get(index)),
                            jobGraph.configuration().get(PipelineOptions.MAX_PARALLELISM));
                }
                Map<String, CheckpointSnapshot.SerializedState> restoredState = restoredCheckpoint == null
                        ? Map.of()
                        : restoredCheckpoint
                                .operatorStates()
                                .getOrDefault(new CheckpointSnapshot.OperatorSubtask(node.getUid(), subtask), Map.of());
                boolean keyedInput = edges.get(index - 1).streamEdge().partitioning() == StreamPartitioning.KEYED;
                OneInputStreamTask task = new OneInputStreamTask(
                        node, environment, stageInputs.get(subtask), output, restoredState, keyedInput);
                inputTasks.add(task);
                bind(execution, task);
            }
        }

        if (jobGraph.configuration().get(RuntimeOptions.SCHEMA_EVOLUTION_ENABLED)) {
            if (restoredCheckpoint != null && restoredCheckpoint.schemaManagerState() == null) {
                throw new IllegalStateException(
                        "Cannot restore schema evolution without the matching schema manager checkpoint state");
            }
            JobVertex sinkVertex = vertices.getLast();
            Object sinkDefinition = sinkVertex.getHeadOperator().getSink().orElseThrow();
            if (!(sinkDefinition instanceof SchemaEvolutionSink schemaSink)) {
                throw new UnsupportedOperationException(
                        "The configured Sink does not declare runtime schema evolution support");
            }
            SchemaManager manager = SchemaCoordinator.restoreManager(
                    restoredCheckpoint == null ? null : restoredCheckpoint.schemaManagerState());
            List<SchemaVersionedTableState> initialSchemaStates = List.copyOf(schemaSink.initialSchemaState());
            if (initialSchemaStates.isEmpty()
                    && io.yak.ops.core.event.Event.class.isAssignableFrom(
                            vertices.getFirst().getHeadOperator().getOutputType())) {
                throw new IllegalStateException("Schema evolution requires admitted initial source/target schemas");
            }
            if (restoredCheckpoint == null) {
                manager.initialize(initialSchemaStates);
            } else {
                manager.validateRestoredRoutes(initialSchemaStates);
            }
            List<SchemaVersionedTableState> restoredSchemaStates = manager.snapshot().tables().stream()
                    .map(state -> new SchemaVersionedTableState(
                            state.sourceTable(),
                            state.targetTable(),
                            state.sourceSchema(),
                            state.targetSchema(),
                            state.version()))
                    .toList();
            schemaSink.restoreSchemaState(restoredSchemaStates);
            Map<Integer, Integer> topologicalOrder = new java.util.HashMap<>();
            for (int index = 0; index < vertices.size(); index++) {
                topologicalOrder.put(vertices.get(index).getHeadOperator().getId(), index);
            }
            List<OneInputStreamTask> orderedInputs = inputTasks.stream()
                    .sorted(java.util.Comparator.comparingInt(
                            task -> topologicalOrder.get(task.taskInfo().operatorId())))
                    .toList();
            List<OneInputStreamTask> sinkTasks = orderedInputs.stream()
                    .filter(OneInputStreamTask::isSinkTask)
                    .toList();
            MetadataApplier applier =
                    Objects.requireNonNull(schemaSink.createMetadataApplier(), "Sink returned a null MetadataApplier");
            schemaCoordinator = new SchemaCoordinator(
                    manager,
                    applier,
                    List.of(),
                    orderedInputs,
                    sinkTasks,
                    this::failJob,
                    schemaSink,
                    jobGraph.configuration()
                            .get(io.yak.ops.core.configuration.SchemaEvolutionOptions.ALLOW_DROP_COLUMNS));
        }

        JobVertex sourceVertex = vertices.getFirst();
        StreamNode sourceNode = sourceVertex.getHeadOperator();
        Source<Object, SourceSplit, Object> source = castSource(sourceNode);
        OperatorCoordinatorContext coordinatorContext =
                new OperatorCoordinatorContext(jobGraph.jobID(), sourceVertex.getId(), sourceVertex.getParallelism());
        Map<Integer, List<SourceSplit>> restoredReaderSplits = Map.of();
        if (restoredCheckpoint != null) {
            restoredReaderSplits = CheckpointCoordinator.restoreSplits(restoredCheckpoint, source);
            Object enumeratorState = CheckpointCoordinator.restoreEnumerator(restoredCheckpoint, source);
            coordinator = SourceCoordinator.restore(
                    source,
                    coordinatorContext,
                    enumeratorState,
                    jobGraph.configuration().get(ExecutionOptions.LOG_ID),
                    executionGraph::reportPlannedSplits);
        } else {
            coordinator = new SourceCoordinator<>(
                    source,
                    coordinatorContext,
                    jobGraph.configuration().get(ExecutionOptions.LOG_ID),
                    executionGraph::reportPlannedSplits);
        }

        JobEdge firstEdge = edges.getFirst();
        for (int subtask = 0; subtask < sourceVertex.getParallelism(); subtask++) {
            Execution execution = executionGraph.currentExecution(sourceVertex.getId(), subtask);
            RecordWriterOutput<Object> output = new RecordWriterOutput<>(
                    firstEdge.streamEdge(),
                    subtask,
                    sourceVertex.getParallelism(),
                    new ResultPartition<>(subtask, inputs.getFirst()),
                    jobGraph.configuration().get(PipelineOptions.MAX_PARALLELISM));
            SourceOperatorStreamTask<Object, SourceSplit> task = new SourceOperatorStreamTask<>(
                    source,
                    coordinator,
                    environment(execution),
                    output,
                    null,
                    restoredReaderSplits.getOrDefault(subtask, List.of()),
                    executionGraph::reportCompletedSplits,
                    schemaCoordinator);
            sourceTasks.add(task);
            bind(execution, task);
        }

        if (schemaCoordinator != null) {
            schemaCoordinator.bindSourceTasks(sourceTasks);
        }

        if (checkpointStore != null) {
            checkpointCoordinator = new CheckpointCoordinator(
                    jobGraph,
                    source,
                    coordinator,
                    sourceTasks,
                    inputTasks,
                    checkpointStore,
                    restoredCheckpoint,
                    executionGraph::isCancellationRequested,
                    this::failJob,
                    schemaCoordinator);
            if (checkpointCoordinator.requiresFinalCheckpoint()) {
                CompletableFuture<Void> completed = checkpointCoordinator.finalCheckpointFuture();
                for (SourceOperatorStreamTask<Object, SourceSplit> task : sourceTasks) {
                    task.requireFinalCheckpoint(completed);
                }
                for (OneInputStreamTask task : inputTasks) {
                    task.requireFinalCheckpoint(completed);
                }
            }
        }
    }

    private void validateFinishedRestore(CheckpointSnapshot snapshot) {
        Set<String> expected = jobGraph.getVertices().stream()
                .map(vertex -> vertex.getHeadOperator().getUid())
                .collect(java.util.stream.Collectors.toSet());
        if (!snapshot.fullyFinishedOperators().equals(expected)
                || !snapshot.finishedSubtasks().isEmpty()
                || !snapshot.operatorStates().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Finished-on-Restore requires a completely finished terminal checkpoint");
        }
    }

    /** Recreates only the network termination protocol of an already completed bounded job. */
    private void assembleFinishedOnRestore(
            List<JobVertex> vertices, List<JobEdge> edges, List<List<InputGate<Object>>> inputs) {
        for (int index = vertices.size() - 1; index >= 0; index--) {
            JobVertex vertex = vertices.get(index);
            for (int subtask = 0; subtask < vertex.getParallelism(); subtask++) {
                Execution execution = executionGraph.currentExecution(vertex.getId(), subtask);
                InputGate<Object> input =
                        index == 0 ? null : inputs.get(index - 1).get(subtask);
                RecordWriterOutput<Object> output = null;
                if (index < vertices.size() - 1) {
                    output = new RecordWriterOutput<>(
                            edges.get(index).streamEdge(),
                            subtask,
                            vertex.getParallelism(),
                            new ResultPartition<>(subtask, inputs.get(index)),
                            jobGraph.configuration().get(PipelineOptions.MAX_PARALLELISM));
                }
                bind(execution, new FinishedOnRestoreStreamTask(environment(execution), input, output));
            }
        }
    }

    private void assembleChainedVertex() throws Exception {
        JobVertex vertex = jobGraph.getVertices().getFirst();
        List<StreamNode> operators = vertex.getOperators();
        StreamNode sourceNode = operators.getFirst();
        StreamNode sinkNode = operators.getLast();
        Source<Object, SourceSplit, Object> source = castSource(sourceNode);
        OperatorCoordinatorContext context =
                new OperatorCoordinatorContext(jobGraph.jobID(), vertex.getId(), vertex.getParallelism());
        coordinator = new SourceCoordinator<>(
                source,
                context,
                jobGraph.configuration().get(ExecutionOptions.LOG_ID),
                executionGraph::reportPlannedSplits);
        OperatorChain chain = new OperatorChain(operators.subList(1, operators.size() - 1), sinkNode);
        Execution execution = executionGraph.currentExecution(vertex.getId(), 0);
        SourceOperatorStreamTask<Object, SourceSplit> task = new SourceOperatorStreamTask<>(
                source,
                coordinator,
                environment(execution),
                chain,
                chain,
                List.of(),
                executionGraph::reportCompletedSplits);
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
        return !jobGraph.configuration()
                        .get(CheckpointingOptions.CHECKPOINTING_INTERVAL)
                        .isZero()
                || jobGraph.configuration().get(CheckpointingOptions.RESTORE_LATEST);
    }

    private void ensureNotCancelled() {
        if (executionGraph.isCancellationRequested() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Local job cancellation has been requested");
        }
    }

    private void failJob(Throwable cause) {
        if (firstFailure.compareAndSet(null, Objects.requireNonNull(cause, "cause must not be null"))) {
            stopAll(cause);
        }
    }

    /**
     * Resolves prepared transactions only against the checkpoint actually published to disk.
     * A missing checkpoint means that every prepared XA transaction of this Instance is orphaned.
     * Corrupt checkpoints fail closed rather than authorizing any rollback or commit.
     */
    @SuppressWarnings("unchecked")
    private void reconcileSinkCommits() throws Exception {
        String directory = jobGraph.configuration().get(CheckpointingOptions.STATE_DIRECTORY);
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(Path.of(directory))) {
            CheckpointSnapshot durable = store.loadLatest(LocalCompletedCheckpointStore.graphSignature(
                            jobGraph.graph(),
                            jobGraph.configuration().get(PipelineOptions.MAX_PARALLELISM),
                            jobGraph.configuration().get(CheckpointingOptions.DEFINITION_IDENTITY)))
                    .orElse(null);
            for (JobVertex vertex : jobGraph.getVertices()) {
                StreamNode node = vertex.getHeadOperator();
                if (!node.isSink() || !(node.getSink().orElseThrow() instanceof SupportsCommitter<?, ?>)) {
                    continue;
                }
                for (int subtask = 0; subtask < vertex.getParallelism(); subtask++) {
                    Execution execution = executionGraph.currentExecution(vertex.getId(), subtask);
                    TaskEnvironment context = environment(execution);
                    var state = durable == null
                            ? Map.<String, CheckpointSnapshot.SerializedState>of()
                            : durable.operatorStates()
                                    .getOrDefault(
                                            new CheckpointSnapshot.OperatorSubtask(node.getUid(), subtask), Map.of());
                    boolean keyed = jobGraph.getEdges().stream()
                            .anyMatch(edge -> edge.targetVertexId() == vertex.getId()
                                    && edge.streamEdge().partitioning() == StreamPartitioning.KEYED);
                    var backend = new OperatorStateBackend(state, context.taskInfo(), keyed);
                    var sink = (Sink<Object>) node.getSink().orElseThrow();
                    new SinkWriterOperator<>(sink, context).recoverCommittables(backend);
                }
            }
        }
    }

    private void stopAll(Throwable cause) {
        // Set the cancellation flag on each task before waking its InputGate. Otherwise a
        // downstream task can observe the interrupted await as an asynchronous failure.
        for (Execution execution : executions) {
            execution.cancelAsync();
        }
        Throwable stopCause = executionGraph.isCancellationRequested()
                        && (unwrap(cause) instanceof InterruptedException
                                || unwrap(cause) instanceof CancellationException)
                ? new CancellationException("Job cancellation requested")
                : cause;
        for (InputGate<Object> inputGate : inputGates) {
            inputGate.abort(stopCause);
        }
    }

    private Throwable closeAll() {
        List<AutoCloseable> resources = new ArrayList<>();
        if (checkpointCoordinator != null) {
            resources.add(checkpointCoordinator);
        }
        if (schemaCoordinator != null) {
            resources.add(schemaCoordinator);
        }
        resources.addAll(executions);
        if (coordinator != null) {
            resources.add(coordinator);
        }
        if (checkpointStore != null) {
            resources.add(checkpointStore);
        }
        return closeAll(resources);
    }

    /**
     * Awaits idempotent deployment shutdown before the Job publishes its terminal outcome.
     * Cancellation interrupts wake execution waits but cannot release resource ownership early.
     *
     * @param resources deployment resources in shutdown order
     * @return accumulated cleanup failure, or null after successful cleanup
     */
    static Throwable closeAll(List<? extends AutoCloseable> resources) {
        boolean interrupted = Thread.interrupted();
        Throwable failure = null;
        try {
            for (AutoCloseable resource : resources) {
                boolean closed = false;
                while (!closed) {
                    try {
                        resource.close();
                        closed = true;
                    } catch (InterruptedException error) {
                        interrupted = true;
                        Thread.interrupted();
                    } catch (Throwable error) {
                        failure = accumulate(failure, error);
                        closed = true;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
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
