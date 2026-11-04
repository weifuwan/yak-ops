package io.yak.ops.scheduler.api;

import io.yak.ops.dao.entity.Schedule;

/**
 * This is the interface for scheduler, contains methods to operate schedule task.
 */
public interface SchedulerApi extends AutoCloseable {

    /**
     * Start the scheduler, if not start, the scheduler will not execute task.
     *
     * @throws SchedulerException if start failed.
     */
    void start() throws SchedulerException;

    /**
     * @param projectId project id, the schedule task belongs to.
     * @param schedule  schedule metadata.
     * @throws SchedulerException if insert/update failed.
     */
    void insertOrUpdateScheduleTask(int projectId, Schedule schedule) throws SchedulerException;

    /**
     * Delete a schedule task.
     *
     * @param projectId  project id, the schedule task belongs to.
     * @param scheduleId schedule id.
     * @throws SchedulerException if delete failed.
     */
    void deleteScheduleTask(int projectId, int scheduleId) throws SchedulerException;

    /**
     * Close the scheduler and release the resource.
     *
     * @throws SchedulerException if close failed.
     */
    void close() throws Exception;
}
