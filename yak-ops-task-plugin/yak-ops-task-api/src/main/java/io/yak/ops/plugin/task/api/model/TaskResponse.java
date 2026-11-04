package io.yak.ops.plugin.task.api.model;

import io.yak.ops.plugin.task.api.enums.TaskRunStatus;

import lombok.Data;

@Data
public class TaskResponse {

    /**
     * varPool string
     */
    private String varPool;

    /**
     * SHELL process pid
     */
    private int processId;

    /**
     * SHELL result string
     */
    private String resultString;

    /**
     * other resource manager appId , for example : YARN etc
     */
    private String appIds;

    private Process process;

    private volatile boolean cancel = false;

    /**
     * exit code
     */
    private volatile int exitStatusCode = -1;

    private TaskRunStatus status;
}
