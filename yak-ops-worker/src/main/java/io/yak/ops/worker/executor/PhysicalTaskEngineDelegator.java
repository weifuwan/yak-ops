package io.yak.ops.worker.executor;

import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.TaskEngine;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** DolphinScheduler PhysicalTaskEngineDelegator, embedded and without remote RPC. */
@Component
public final class PhysicalTaskEngineDelegator implements AutoCloseable {
    private final TaskEngine engine;
    private final PhysicalTaskExecutorRepository repository;

    public PhysicalTaskEngineDelegator(@Value("${yak.worker.task-executor-slots:4}") int slots) {
        repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider container = new PhysicalTaskExecutorContainerProvider(slots);
        engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, container);
    }

    @PostConstruct
    public void start() {
        engine.start();
    }

    public void dispatch(
            String workspaceId,
            String instanceId,
            TaskPluginRegistry registry,
            BiFunction<TaskExecutionContext, Supplier<ExecutableTaskPlugin>, PhysicalTask> prepareTask) {
        TaskExecutionContext context = new TaskExecutionContext(workspaceId, instanceId);
        PhysicalTaskPluginFactory pluginFactory = new PhysicalTaskPluginFactory(registry, prepareTask);
        engine.submitTask(new PhysicalTaskExecutorFactory(pluginFactory).createTaskExecutor(context));
    }

    public void kill(String instanceId) {
        engine.killTask(instanceId);
    }

    public void registerTaskHandle(String instanceId, TaskHandle handle) {
        PhysicalTaskExecutor executor = (PhysicalTaskExecutor) repository
                .get(instanceId)
                .orElseThrow(() -> new IllegalStateException("Physical Task is not registered: " + instanceId));
        executor.registerTaskHandle(handle);
    }

    public Optional<TaskHandle> taskHandle(String instanceId) {
        return repository
                .get(instanceId)
                .filter(PhysicalTaskExecutor.class::isInstance)
                .map(PhysicalTaskExecutor.class::cast)
                .flatMap(PhysicalTaskExecutor::getTaskHandle);
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
