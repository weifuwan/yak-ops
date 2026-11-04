package io.yak.ops.scheduler.api;

public class SchedulerException extends RuntimeException {

    public SchedulerException(SchedulerExceptionEnum schedulerExceptionEnum) {
        super("Scheduler[" + schedulerExceptionEnum.getCode() + "] " + schedulerExceptionEnum.getMessage());
    }

    public SchedulerException(SchedulerExceptionEnum schedulerExceptionEnum, Throwable cause) {
        super("Scheduler[" + schedulerExceptionEnum.getCode() + "] " + schedulerExceptionEnum.getMessage(), cause);
    }

}
