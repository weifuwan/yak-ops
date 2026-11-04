package io.yak.ops.task.executor.eventbus;

import io.yak.ops.task.executor.events.IReportableTaskExecutorLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorLifecycleEventType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The interface of task executor event reporter, used to report task execution events to the master.
 * <p> Once the task executor lifecycle changed, need to report the event to the master.
 */
public interface ITaskExecutorLifecycleEventReporter extends AutoCloseable {

    /**
     * Start the reporter.
     */
    void start();

    /**
     * Receive the task executor event, which need to be reported to the master.
     * <p> This method is not sync, will only put the event into a pending queue and send the event in another thread.
     */
    void reportTaskExecutorLifecycleEvent(final IReportableTaskExecutorLifecycleEvent reportableTaskExecutorLifecycleEvent);

    /**
     * Receive the task execution event ACK, which means the master has received the event.
     * <p> Once the reporter received the ACK, will remove the event from the pending queue, otherwise will retry to send the event to master at interval.
     */
    void receiveTaskExecutorLifecycleEventACK(final TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck);

    /**
     * Reset the events in the channel to allow them to be reported immediately.
     */
    void onWorkflowInstanceHostChanged(int taskInstanceId);

    /**
     * Shutdown the reporter.
     */
    @Override
    void close();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class TaskExecutorLifecycleEventAck {

        private int taskExecutorId;

        private TaskExecutorLifecycleEventType taskExecutorLifecycleEventType;
    }

}
