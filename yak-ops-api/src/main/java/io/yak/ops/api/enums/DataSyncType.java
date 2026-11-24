package io.yak.ops.api.enums;

import io.yak.ops.common.enums.TaskExecuteType;

public enum DataSyncType {

    OFFLINE(TaskExecuteType.BATCH),
    REALTIME(TaskExecuteType.STREAM);

    private final TaskExecuteType taskExecuteType;

    DataSyncType(TaskExecuteType taskExecuteType) {
        this.taskExecuteType = taskExecuteType;
    }

    public TaskExecuteType toTaskExecuteType() {
        return taskExecuteType;
    }
}
