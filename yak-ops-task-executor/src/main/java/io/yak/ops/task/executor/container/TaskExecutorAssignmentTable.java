package io.yak.ops.task.executor.container;

import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.worker.ITaskExecutorWorker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaskExecutorAssignmentTable {

    private final Map<Integer, Integer> assignedTaskExecutorIdToWorkerIdMapping = new ConcurrentHashMap<>();

    public void registerTaskExecutor(final ITaskExecutor taskExecutor, final ITaskExecutorWorker taskExecutorWorker) {
        assignedTaskExecutorIdToWorkerIdMapping.put(taskExecutor.getId(), taskExecutorWorker.getId());
    }

    public boolean isTaskExecutorRegistered(final ITaskExecutor taskExecutor) {
        return assignedTaskExecutorIdToWorkerIdMapping.containsKey(taskExecutor.getId());
    }

    public void unregisterTaskExecutor(final ITaskExecutor taskExecutor) {
        assignedTaskExecutorIdToWorkerIdMapping.remove(taskExecutor.getId());
    }

    public Integer getTaskExecutorWorkerId(final ITaskExecutor taskExecutor) {
        return assignedTaskExecutorIdToWorkerIdMapping.get(taskExecutor.getId());
    }

    public boolean isEmpty() {
        return assignedTaskExecutorIdToWorkerIdMapping.isEmpty();
    }
}
