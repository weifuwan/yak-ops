package io.yak.ops.scheduler.quartz.exception;

import io.yak.ops.scheduler.api.SchedulerExceptionEnum;

public enum QuartzSchedulerExceptionEnum implements SchedulerExceptionEnum {

    QUARTZ_SCHEDULER_START_ERROR("QUARTZ-001", "Quartz Scheduler start error"),
    QUARTZ_UPSERT_JOB_ERROR("QUARTZ-002", "Upsert quartz job error"),
    QUARTZ_DELETE_JOB_ERROR("QUARTZ-003", "Delete quartz job error"),
    QUARTZ_SCHEDULER_SHOWDOWN_ERROR("QUARTZ-004", "Quartz Scheduler shutdown error"),
    ;

    private final String code;

    private final String message;

    QuartzSchedulerExceptionEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
