package io.yak.ops.server.master.engine.task.dispatcher;

import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.engine.task.client.ITaskExecutorClient;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;

/**
 * WorkerGroupTaskDispatcherManager is responsible for managing the task dispatching for worker groups.
 * It maintains a mapping of worker groups to their task dispatchers and priority delay queues,
 * and supports adding tasks, starting and stopping worker groups, as well as cleaning up resources upon shutdown.
 */
@Component
@Slf4j
public class WorkerGroupDispatcherCoordinator implements AutoCloseable {

    @Autowired
    private ITaskExecutorClient taskExecutorClient;

    private final Map<String, WorkerGroupDispatcher> workerGroupDispatchers;

    private final MasterConfig masterConfig;

    public WorkerGroupDispatcherCoordinator(final MasterConfig masterConfig) {
        workerGroupDispatchers = new ConcurrentHashMap<>();
        this.masterConfig = masterConfig;
    }

    public void start() {
        log.info("WorkerGroupTaskDispatcherManager started...");
    }

    /**
     * Dispatch task to the worker group with the specified remaining time.
     */
    public void dispatchTask(final ITaskExecution taskExecution,
                             final long delayTimeMills) {
        final String workerGroup = taskExecution.getTaskInstance().getWorkerGroup();
        getOrCreateWorkerGroupDispatcher(workerGroup).dispatchTask(taskExecution, delayTimeMills);
        log.info("Success add Task[id={}] to WorkerGroupDispatcher[name={}]", taskExecution.getId(),
                workerGroup);
    }

    /**
     * Remove task from the dispatcher.
     * <p> If the task doesn't exist in the dispatcher, it will return false, this means the task might already be dispatched.
     */
    public boolean removeTask(ITaskExecution taskExecution) {
        final String workerGroup = taskExecution.getTaskInstance().getWorkerGroup();
        boolean removed = getOrCreateWorkerGroupDispatcher(workerGroup).removeTask(taskExecution);
        if (removed) {
            log.info("Success removed Task[id={}] from WorkerGroupDispatcher[name={}]",
                    taskExecution.getId(), workerGroup);
        } else {
            log.info("Failed to remove Task[id={}] from WorkerGroupDispatcher[name={}], this task has been dispatched",
                    taskExecution.getId(), workerGroup);
        }
        return removed;
    }

    public boolean existWorkerGroup(String workerGroup) {
        return workerGroupDispatchers.containsKey(workerGroup);
    }

    @VisibleForTesting
    public Map<String, WorkerGroupDispatcher> workerGroupDispatchers() {
        return workerGroupDispatchers;
    }

    /**
     * Stop all workerGroupTaskDispatchWaitingQueueLooper
     */
    @Override
    public void close() throws Exception {
        log.info("WorkerGroupDispatcherCoordinator closing");
        for (WorkerGroupDispatcher workerGroupDispatcher : workerGroupDispatchers.values()) {
            try {
                workerGroupDispatcher.close();
            } catch (Exception e) {
                log.error("close WorkerGroupDispatcher[name={}] error", workerGroupDispatcher.getName(), e);
            }
        }
        log.info("WorkerGroupDispatcherCoordinator closed...");
    }

    private WorkerGroupDispatcher getOrCreateWorkerGroupDispatcher(String workerGroup) {
        return workerGroupDispatchers.computeIfAbsent(workerGroup, wg -> {
            WorkerGroupDispatcher workerGroupDispatcher =
                    new WorkerGroupDispatcher(wg, taskExecutorClient, masterConfig.getTaskDispatchPolicy());
            workerGroupDispatcher.start();
            return workerGroupDispatcher;
        });
    }
}
