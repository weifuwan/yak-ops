package io.yak.ops.worker.task.executor.container;

import io.yak.ops.worker.task.executor.ITaskExecutor;
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
        if (candidate.isEmpty()) throw new IllegalStateException("All exclusive task executor slots are busy");
        TaskExecutorWorker worker = candidate.get();
        worker.registerTaskExecutor(executor);
        assignmentTable.registerTaskExecutor(executor, worker);
    }

    @Override
    public void start(ITaskExecutor executor) {
        String workerId = assignmentTable.getTaskExecutorWorkerId(executor);
        if (workerId == null) throw new IllegalStateException("Task not assigned to a worker");
        taskExecutorWorkers.getWorkerById(Integer.parseInt(workerId)).fireTaskExecutor(executor);
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
        String id = assignmentTable.getTaskExecutorWorkerId(executor);
        if (id != null) {
            TaskExecutorWorker worker = taskExecutorWorkers.getWorkerById(Integer.parseInt(id));
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

    protected abstract Optional<TaskExecutorWorker> getTaskExecutorWorkerCandidate(ITaskExecutor executor);
}
