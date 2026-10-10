package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.ProcessingTimeService;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.configuration.Configuration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Runtime environment for one local Task, exposing identity, configuration and cancellation state.
 *
 * <p>This object does not own SourceCoordinator, Reader, Gateway or database connections.
 * Source-specific event delivery is assembled by its owning Source task.
 */
public final class TaskEnvironment implements WriterInitContext {

    private final RuntimeTaskInfo taskInfo;
    private final Configuration configuration;
    private final BooleanSupplier cancellationRequested;
    private final ProcessingTimeService processingTimeService;

    public TaskEnvironment(RuntimeTaskInfo taskInfo, Configuration configuration) {
        this(taskInfo, configuration, () -> false, null);
    }

    private TaskEnvironment(
            RuntimeTaskInfo taskInfo,
            Configuration configuration,
            BooleanSupplier cancellationRequested,
            ProcessingTimeService processingTimeService) {
        this.taskInfo = Objects.requireNonNull(taskInfo, "taskInfo 不能为空");
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration 不能为空"));
        this.cancellationRequested = Objects.requireNonNull(cancellationRequested, "cancellationRequested 不能为空");
        this.processingTimeService = processingTimeService;
    }

    public RuntimeTaskInfo taskInfo() {
        return taskInfo;
    }

    @Override
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration();
    }

    /** Returns a defensive configuration copy that callers cannot use to mutate task settings. */
    public Configuration configuration() {
        return new Configuration(configuration);
    }

    /**
     * Returns a new task environment using the supplied cancellation signal.
     *
     * <p>The original environment and its configuration snapshot remain unchanged.
     */
    public TaskEnvironment withCancellation(BooleanSupplier cancellationRequested) {
        return new TaskEnvironment(taskInfo, configuration, cancellationRequested, processingTimeService);
    }

    /** Attaches a task-owned timer service without changing other Writer context values. */
    public TaskEnvironment withProcessingTimeService(ProcessingTimeService service) {
        return new TaskEnvironment(
                taskInfo, configuration, cancellationRequested, Objects.requireNonNull(service, "service"));
    }

    /** Preserves mailbox timers and cancellation for a logical Sink inside an inline operator chain. */
    public TaskEnvironment forSubtask(RuntimeTaskInfo sinkTaskInfo) {
        return new TaskEnvironment(sinkTaskInfo, configuration, cancellationRequested, processingTimeService);
    }

    @Override
    public ProcessingTimeService getProcessingTimeService() {
        if (processingTimeService == null) {
            throw new UnsupportedOperationException("Task processing-time timers are not bound");
        }
        return processingTimeService;
    }

    /** Returns whether cancellation was requested by the owning task, without changing task state. */
    public boolean isCancellationRequested() {
        return cancellationRequested.getAsBoolean();
    }
}
