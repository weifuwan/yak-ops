package io.yak.ops.task.executor;

import io.yak.ops.task.executor.dto.TaskExecutorDTO;
import io.yak.ops.task.executor.exceptions.TaskExecutorNotFoundException;
import io.yak.ops.task.executor.exceptions.TaskExecutorRuntimeException;

import java.util.List;

/**
 * The TaskEngine interface used to responsible for task runtime.
 */
public interface ITaskEngine extends AutoCloseable {

    /**
     * Start the task engine.
     */
    void start();

    /**
     * Query all task executors info.
     *
     * @return list of task executor DTOs
     */
    List<TaskExecutorDTO> queryTaskExecutors();

    /**
     * Submit the task to the task engine.
     *
     * @param taskExecutor the task executor to be submitted
     * @throws TaskExecutorRuntimeException if submit failed.
     */
    void submitTask(final ITaskExecutor taskExecutor) throws TaskExecutorRuntimeException;

    /**
     * Pause the task by the task id.
     * <p> This method will not block, only send the pause signal to the task executor
     *
     * @param taskExecutorId the id of the task executor will be paused
     * @throws TaskExecutorNotFoundException If cannot find the task executor.
     */
    void pauseTask(final int taskExecutorId) throws TaskExecutorNotFoundException;

    /**
     * Kill the task by the task id.
     * <p> This method will not block, only send the kill signal to the task executor.
     *
     * @param taskExecutorId the id of the task executor will be killed.
     * @throws TaskExecutorNotFoundException If cannot find the task executor.
     */
    void killTask(final int taskExecutorId) throws TaskExecutorNotFoundException;

    /**
     * Shutdown the task engine.
     */
    @Override
    void close();

}
