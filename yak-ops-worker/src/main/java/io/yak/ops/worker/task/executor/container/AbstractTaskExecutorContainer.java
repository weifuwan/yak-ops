package io.yak.ops.worker.task.executor.container;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.exceptions.TaskExecutorRuntimeException;
import io.yak.ops.worker.task.executor.worker.TaskExecutorWorker;
import io.yak.ops.worker.task.executor.worker.TaskExecutorWorkers;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** DolphinScheduler exclusive/shared allocation with worker thread shutdown and no remote log upload. */
public abstract class AbstractTaskExecutorContainer implements ITaskExecutorContainer {
    private final TaskExecutorAssignmentTable assignmentTable = new TaskExecutorAssignmentTable();
    protected final TaskExecutorWorkers taskExecutorWorkers;
    private final ExecutorService taskExecutorThreadPool;
    private volatile boolean closed;

    protected AbstractTaskExecutorContainer(TaskExecutorContainerConfig config) {
        int size = config.getTaskExecutorThreadPoolSize();
        if (size < 1) throw new IllegalArgumentException("Task executor slots must be positive");
        this.taskExecutorWorkers = new TaskExecutorWorkers(size);
        this.taskExecutorThreadPool = Executors.newFixedThreadPool(size);
        for (TaskExecutorWorker worker : taskExecutorWorkers.getWorkers()) {
            taskExecutorThreadPool.submit(worker::start);
        }
    }

    @Override
    public synchronized void dispatch(ITaskExecutor executor) {
        if (closed) throw new IllegalStateException("Task container is closed");
        Optional<TaskExecutorWorker> candidate = getTaskExecutorWorkerCandidate(executor);
        if (candidate.isEmpty()) throw new TaskExecutorRuntimeException("All exclusive task executor slots are busy");
        TaskExecutorWorker worker = candidate.get();
        worker.registerTaskExecutor(executor);
        try {
            assignmentTable.registerTaskExecutor(executor, worker);
        } catch (RuntimeException failure) {
            worker.unRegisterTaskExecutor(executor);
            throw failure;
        }
    }

    @Override
    public void start(ITaskExecutor executor) {
        Integer workerId = assignmentTable.getTaskExecutorWorkerId(executor);
        if (workerId == null) throw new IllegalStateException("Task not assigned to a worker");
        taskExecutorWorkers.getWorkerById(workerId).fireTaskExecutor(executor);
    }

    @Override
    public void pause(ITaskExecutor executor) {
        executor.pause();
    }

    @Override
    public void kill(ITaskExecutor executor) {
        executor.kill();
    }

    @Override
    public synchronized void finalize(ITaskExecutor executor) {
        Integer id = assignmentTable.getTaskExecutorWorkerId(executor);
        if (id != null) {
            TaskExecutorWorker worker = taskExecutorWorkers.getWorkerById(id);
            worker.unFireTaskExecutor(executor);
            worker.unRegisterTaskExecutor(executor);
            assignmentTable.unregisterTaskExecutor(executor);
        }
        executor.finalizeTask();
    }

    @Override
    public double slotUsage() {
        long inUse = taskExecutorWorkers.getWorkers().stream()
                .filter(worker -> worker.getRegisteredTaskExecutorSize() > 0)
                .count();
        return (double) inUse / taskExecutorWorkers.getWorkers().size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        taskExecutorThreadPool.shutdownNow();
    }

    public TaskExecutorAssignmentTable getTaskExecutorAssignmentTable() {
        return assignmentTable;
    }

    public TaskExecutorWorkers getTaskExecutorWorkers() {
        return taskExecutorWorkers;
    }

    protected abstract Optional<TaskExecutorWorker> getTaskExecutorWorkerCandidate(ITaskExecutor executor);
}
