package io.yak.ops.scheduler.quartz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.quartz.JobKey;

class QuartzJobKeyTest {

    @Test
    void of() {
        QuartzJobKey quartzJobKey = QuartzJobKey.of(1, 2);
        assertEquals(1, quartzJobKey.getProjectId());
        assertEquals(2, quartzJobKey.getSchedulerId());
    }

    @Test
    void toJobKey() {
        QuartzJobKey quartzJobKey = QuartzJobKey.of(1, 2);
        JobKey jobKey = quartzJobKey.toJobKey();
        assertEquals("job_2", jobKey.getName());
        assertEquals("jobgroup_1", jobKey.getGroup());
    }
}
