package io.yak.ops.server.master.engine.task.lifecycle;

import io.yak.ops.server.master.engine.ILifecycleEventType;

public enum TaskLifecycleEventType implements ILifecycleEventType {

    /**
     * Start the Task instance.
     */
    START,
    /**
     * Dispatch the task instance to target.
     */
    DISPATCH,
    /**
     * Task instance encounters catastrophic failure(such as initialization failure), it will enter a failed state.
     */
    FATAL,
    /**
     * The task instance is dispatched to the target executor server.
     */
    DISPATCHED,
    /**
     * // todo: maybe we can remove this event, once the task has been dispatched it should start
     * The task instance is running at the target executor server.
     */
    RUNNING,
    /**
     * The task instance's runtime context changed.
     */
    RUNTIME_CONTEXT_CHANGED,
    /**
     * Do Timeout strategy of the task instance.
     */
    TIMEOUT,
    /**
     * Retry the task instance.
     */
    RETRY,
    /**
     * Pause the task instance.
     */
    PAUSE,
    /**
     * The task instance is paused.
     */
    PAUSED,
    /**
     * Failover the task instance.
     */
    FAILOVER,
    /**
     * Kill the task instance.
     */
    KILL,
    /**
     * The task instance is killed.
     */
    KILLED,
    /**
     * The task instance is success.
     */
    SUCCEEDED,
    /**
     * The task instance is failed.
     */
    FAILED,
    ;

}
