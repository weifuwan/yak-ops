package io.yak.ops.worker.executor;

import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.TaskEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Embedded physical Worker entry point: one JVM, no RPC or registry. */
@Component
public final class PhysicalTaskEngineDelegator implements AutoCloseable {
    private final TaskEngine engine;
    private final PhysicalTaskExecutorFactory factory = new PhysicalTaskExecutorFactory();

    public PhysicalTaskEngineDelegator(@Value("${yak.worker.task-executor-slots:4}") int slots) {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider container = new PhysicalTaskExecutorContainerProvider(slots);
        this.engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, container);
    }

    @PostConstruct
    public void start() {
        engine.start();
    }

    public void dispatch(
            String workspaceId,
            String instanceId,
            Supplier<CompletionStage<TaskOutcome>> launch,
            Runnable cancellation) {
        engine.submitTask(factory.createTaskExecutor(workspaceId, instanceId, launch, cancellation));
    }

    public void kill(String instanceId) {
        engine.killTask(instanceId);
    }

    public Collection<ITaskExecutor> queryTaskExecutors() {
        return engine.queryTaskExecutors();
    }

    @PreDestroy
    @Override
    public void close() {
        engine.close();
    }
}
