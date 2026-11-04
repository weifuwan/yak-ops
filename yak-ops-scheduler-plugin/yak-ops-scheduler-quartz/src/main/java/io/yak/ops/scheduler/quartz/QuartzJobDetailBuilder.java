package io.yak.ops.scheduler.quartz;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;

public class QuartzJobDetailBuilder {

    private Integer projectId;

    private Integer scheduleId;

    public static QuartzJobDetailBuilder newBuilder() {
        return new QuartzJobDetailBuilder();
    }

    public QuartzJobDetailBuilder withProjectId(Integer projectId) {
        this.projectId = projectId;
        return this;
    }

    public QuartzJobDetailBuilder withSchedule(Integer scheduleId) {
        this.scheduleId = scheduleId;
        return this;
    }

    public JobDetail build() {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId cannot be null");
        }
        if (scheduleId == null) {
            throw new IllegalArgumentException("scheduleId cannot be null");
        }
        QuartzJobData quartzJobData = QuartzJobData.of(projectId, scheduleId);

        return JobBuilder.newJob(ProcessScheduleTask.class)
                .withIdentity(QuartzJobKey.of(projectId, scheduleId).toJobKey())
                .setJobData(quartzJobData.toJobDataMap())
                .build();
    }

}
