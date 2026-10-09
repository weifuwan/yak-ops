package io.yak.ops.core.api.common;

/**
 * Read-only metadata for a running subtask exposed to connectors and operators.
 *
 * <p>Parallelism is the resolved operator parallelism, not a configuration default. This
 * contract does not expose task threads, mailboxes or execution-graph ownership.
 */
public interface TaskInfo {

    /** Returns this subtask's zero-based index within the operator parallelism. */
    int getIndexOfThisSubtask();

    /** Returns the resolved parallelism of this operator. */
    int getNumberOfParallelSubtasks();

    /** Returns this subtask's execution-attempt number. */
    int getAttemptNumber();

    /** Returns the stable maximum number of key groups, not the current subtask count. */
    int getMaxNumberOfParallelSubtasks();
}
