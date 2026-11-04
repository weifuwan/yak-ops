package io.yak.ops.server.master.engine.workflow.lifecycle;

import io.yak.ops.server.master.engine.ILifecycleEventType;

public enum WorkflowLifecycleEventType implements ILifecycleEventType {

    /**
     * Start the workflow instance
     */
    START,
    /**
     * Notify the workflow instance there exist a task has been finished, and should do DAG topology logic transaction.
     */
    TOPOLOGY_LOGICAL_TRANSACTION_WITH_TASK_FINISH,
    /**
     * Do Timeout strategy of the workflow instance.
     */
    TIMEOUT,
    /**
     * Pause the workflow instance
     */
    PAUSE,
    /**
     * The workflow instance has been paused
     */
    PAUSED,
    /**
     * Stop the workflow instance
     */
    STOP,
    /**
     * The workflow instance has been stopped
     */
    STOPPED,
    /**
     * The workflow instance has been success
     */
    SUCCEED,
    /**
     * The workflow instance has been failed
     */
    FAILED,
    /**
     * Finalize the workflow instance.
     */
    FINALIZE,

}
