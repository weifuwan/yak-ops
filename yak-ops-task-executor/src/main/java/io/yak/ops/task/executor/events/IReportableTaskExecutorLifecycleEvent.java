package io.yak.ops.task.executor.events;

import io.yak.ops.task.executor.ITaskExecutor;

/**
 * The lifecycle event of {@link ITaskExecutor} which should be report to master.
 */
public interface IReportableTaskExecutorLifecycleEvent extends ITaskExecutorLifecycleEvent {

    /**
     * The id of the workflow instance which the event should report to.
     */
    int getWorkflowInstanceId();

    /**
     * Get the latest report time of the event, if the event is never reported, return null.
     */
    Long getLatestReportTime();

    /**
     * Set the latest report time of the event.
     */
    void setLatestReportTime(Long latestReportTime);

}
