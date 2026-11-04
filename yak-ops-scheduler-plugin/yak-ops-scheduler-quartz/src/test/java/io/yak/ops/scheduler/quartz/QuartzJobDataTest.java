package io.yak.ops.scheduler.quartz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;

import com.google.common.collect.ImmutableMap;

class QuartzJobDataTest {

    @Test
    void of() {
        QuartzJobData quartzJobData = QuartzJobData.of(1, 2);
        assertEquals(1, quartzJobData.getProjectId());
        assertEquals(2, quartzJobData.getScheduleId());
    }

    @Test
    void of_JobDataMap() {

        ImmutableMap<String, Object> map = ImmutableMap.of(
                "projectId", 1,
                "scheduleId", 2);
        JobDataMap jobDataMap = new JobDataMap(map);
        QuartzJobData quartzJobData = QuartzJobData.of(jobDataMap);
        assertEquals(1, quartzJobData.getProjectId());
        assertEquals(2, quartzJobData.getScheduleId());
    }

    @Test
    void toJobDataMap() {
        QuartzJobData quartzJobData = QuartzJobData.of(1, 2);
        JobDataMap jobDataMap = quartzJobData.toJobDataMap();
        QuartzJobData quartzJobData1 = QuartzJobData.of(jobDataMap);
        assertEquals(1, quartzJobData1.getProjectId());
        assertEquals(2, quartzJobData1.getScheduleId());
    }
}
