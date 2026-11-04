package io.yak.ops.task.executor.container;

import static ch.qos.logback.classic.ClassicConstants.FINALIZE_SESSION_MARKER;

import io.yak.ops.common.log.remote.RemoteLogUtils;
import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.exceptions.TaskExecutorRuntimeException;
import io.yak.ops.task.executor.worker.TaskExecutorWorker;
import io.yak.ops.task.executor.worker.TaskExecutorWorkers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTaskExecutorContainer implements ITaskExecutorContainer {

    private final TaskExecutorAssignmentTable taskExecutorAssignmentTable;

    protected final ThreadPoolExecutor taskExecutorThreadPool;

    protected final TaskExecutorWorkers taskExecutorWorkers;

    public AbstractTaskExecutorContainer(final TaskExecutorContainerConfig containerConfig) {
        final String threadPoolFormat = containerConfig.getContainerName() + "-worker-%d";
        final int threadPoolSize = containerConfig.getTaskExecutorThreadPoolSize();
        this.taskExecutorThreadPool = ThreadUtils.newDaemonFixedThreadExecutor(threadPoolFormat, threadPoolSize);
        this.taskExecutorWorkers = new TaskExecutorWorkers(threadPoolSize);
        this.taskExecutorAssignmentTable = new TaskExecutorAssignmentTable();
        startAllThreadTaskExecutorWorker();
    }

    @Override
    public void dispatch(final ITaskExecutor taskExecutor) {
        synchronized (this) {
            Optional<TaskExecutorWorker> taskExecutorWorkerCandidate = getTaskExecutorWorkerCandidate(taskExecutor);
            if (!taskExecutorWorkerCandidate.isPresent()) {
                log.info("All ExclusiveThreadTaskExecutorWorker are busy, cannot submit taskExecutor(id={})",
                        taskExecutor.getId());
                throw new TaskExecutorRuntimeException("All ExclusiveThreadTaskExecutorWorker are busy");
            }
            final TaskExecutorWorker taskExecutorWorker = taskExecutorWorkerCandidate.get();
            taskExecutorWorker.registerTaskExecutor(taskExecutor);
            taskExecutorAssignmentTable.registerTaskExecutor(taskExecutor, taskExecutorWorker);
        }
    }

    @Override
    public void start(final ITaskExecutor taskExecutor) {
        final Integer workerId = taskExecutorAssignmentTable.getTaskExecutorWorkerId(taskExecutor);
        if (workerId == null) {
            throw new IllegalStateException(
                    "The taskExecutor: " + taskExecutor.getId() + " is not registered to any worker");
        }
        final TaskExecutorWorker taskExecutorWorker = taskExecutorWorkers.getWorkerById(workerId);
        taskExecutorWorker.fireTaskExecutor(taskExecutor);
    }

    @Override
    public void pause(final ITaskExecutor taskExecutor) {
        taskExecutor.pause();
    }

    @Override
    public void kill(final ITaskExecutor taskExecutor) {
        taskExecutor.kill();
    }

    @Override
    public void finalize(final ITaskExecutor taskExecutor) {
        if (taskExecutorAssignmentTable.isTaskExecutorRegistered(taskExecutor)) {
            final Integer taskExecutorWorkerId = taskExecutorAssignmentTable.getTaskExecutorWorkerId(taskExecutor);
            taskExecutorWorkers.getWorkerById(taskExecutorWorkerId).unRegisterTaskExecutor(taskExecutor);
            taskExecutorAssignmentTable.unregisterTaskExecutor(taskExecutor);
        }
        log.info(FINALIZE_SESSION_MARKER, FINALIZE_SESSION_MARKER.toString());
        pushTaskExecutorLogToRemote(taskExecutor);
        // [Fix-17520]
        taskExecutor.finalizeTask();
    }

    @Override
    public double slotUsage() {
        final List<TaskExecutorWorker> allWorkers = taskExecutorWorkers.getWorkers();
        final long activeWorkerCount = allWorkers
                .stream()
                .filter(taskExecutorWorker -> taskExecutorWorker.getRegisteredTaskExecutorSize() != 0)
                .count();
        return activeWorkerCount / (double) allWorkers.size();
    }

    @VisibleForTesting
    public TaskExecutorAssignmentTable getTaskExecutorAssignmentTable() {
        return taskExecutorAssignmentTable;
    }

    @VisibleForTesting
    public TaskExecutorWorkers getTaskExecutorWorkers() {
        return taskExecutorWorkers;
    }

    private void startAllThreadTaskExecutorWorker() {
        for (final TaskExecutorWorker taskExecutorWorker : taskExecutorWorkers.getWorkers()) {
            taskExecutorThreadPool.submit(taskExecutorWorker::start);
        }
    }

    /**
     * Get the candidate worker to execute the task executor.
     * <p> If the container is full, return empty.
     */
    protected abstract Optional<TaskExecutorWorker> getTaskExecutorWorkerCandidate(final ITaskExecutor taskExecutor);

    private void pushTaskExecutorLogToRemote(final ITaskExecutor taskExecutor) {
        // todo: move this to customer listener, e.g. RemoteLogApaptor
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        try {
            if (RemoteLogUtils.isRemoteLoggingEnable()) {
                RemoteLogUtils.sendRemoteLog(taskExecutionContext.getLogPath());
                log.info("Send task log {} to remote storage successfully", taskExecutionContext.getLogPath());
            }
        } catch (Exception ex) {
            log.error("Send task log {} to remote storage failed", taskExecutionContext.getLogPath(), ex);
        }
    }

}
