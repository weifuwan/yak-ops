package io.yak.ops.scheduler.quartz.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.scheduler.api.SchedulerException;

import org.junit.jupiter.api.Test;

class QuartzSchedulerExceptionEnumTest {

    @Test
    void testException() {
        SchedulerException schedulerException =
                new SchedulerException(QuartzSchedulerExceptionEnum.QUARTZ_SCHEDULER_START_ERROR);
        assertEquals("Scheduler[QUARTZ-001] Quartz Scheduler start error", schedulerException.getMessage());
    }

}
