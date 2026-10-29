package io.yak.ops.worker.task.executor.container;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.worker.ITaskExecutorWorker;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskExecutorAssignmentTable {
    private final Map<String, Integer> assignments = new ConcurrentHashMap<>();

    public void registerTaskExecutor(ITaskExecutor task, ITaskExecutorWorker worker) {
        if (assignments.putIfAbsent(task.getId(), worker.getId()) != null) {
            throw new IllegalStateException("Task is already assigned: " + task.getId());
        }
    }

    public boolean isTaskExecutorRegistered(ITaskExecutor task) {
        return assignments.containsKey(task.getId());
    }

    public void unregisterTaskExecutor(ITaskExecutor task) {
        assignments.remove(task.getId());
    }

    public Integer getTaskExecutorWorkerId(ITaskExecutor task) {
        return assignments.get(task.getId());
    }

    public boolean isEmpty() {
        return assignments.isEmpty();
    }
}
