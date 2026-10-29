package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import java.util.Optional;
import io.yak.ops.worker.task.executor.TaskEngine;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** DolphinScheduler PhysicalTaskEngineDelegator, embedded and without remote RPC. */
@Component
public final class PhysicalTaskEngineDelegator implements AutoCloseable {
    private final TaskEngine engine;
    private final PhysicalTaskExecutorRepository repository;
    private final PhysicalTaskExecutorFactory factory = new PhysicalTaskExecutorFactory();

    public PhysicalTaskEngineDelegator(@Value("${yak.worker.task-executor-slots:4}") int slots) {
        repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider container = new PhysicalTaskExecutorContainerProvider(slots);
        engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, container);
    }

    @PostConstruct public void start() { engine.start(); }

    public void dispatch(String workspaceId, String instanceId, PhysicalTaskPluginFactory pluginFactory) {
        TaskExecutionContext context = new TaskExecutionContext(workspaceId, instanceId);
        context.setPhysicalTaskPluginFactory(pluginFactory);
        engine.submitTask(factory.createTaskExecutor(context));
    }

    public void kill(String instanceId) { engine.killTask(instanceId); }

    public void registerTaskHandle(String instanceId, TaskHandle handle) {
        PhysicalTaskExecutor executor = (PhysicalTaskExecutor) repository.get(instanceId)
                .orElseThrow(() -> new IllegalStateException("Physical Task is not registered: " + instanceId));
        executor.registerTaskHandle(handle);
    }

    public Optional<TaskHandle> taskHandle(String instanceId) {
        return repository.get(instanceId)
                .filter(PhysicalTaskExecutor.class::isInstance)
                .map(PhysicalTaskExecutor.class::cast)
                .flatMap(PhysicalTaskExecutor::getTaskHandle);
    }
    public Collection<ITaskExecutor> queryTaskExecutors() { return engine.queryTaskExecutors(); }

    @PreDestroy @Override public void close() { engine.close(); }
}
