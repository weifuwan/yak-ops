package io.yak.ops.core.api.common;

/**
 * 向 Connector/Operator 暴露的只读子任务元信息；不持有线程、Mailbox 或执行图。
 * 并行度为本次执行解析后的实际值，而非 Configuration 默认并行度。
 */
public interface TaskInfo {

    int getIndexOfThisSubtask();

    int getNumberOfParallelSubtasks();

    int getAttemptNumber();

    /** The stable number of key-groups, not the currently deployed subtask count. */
    int getMaxNumberOfParallelSubtasks();
}
