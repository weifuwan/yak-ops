package io.yak.ops.scheduler.quartz;

import org.quartz.JobDataMap;

import lombok.Getter;

@Getter
public class QuartzJobData {

    private static final String PROJECT_ID = "projectId";
    private static final String SCHEDULE_ID = "scheduleId";

    private final Integer projectId;

    private final Integer scheduleId;

    private QuartzJobData(Integer projectId, Integer scheduleId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId cannot be null");
        }
        if (scheduleId == null) {
            throw new IllegalArgumentException("schedule cannot be null");
        }
        this.projectId = projectId;
        this.scheduleId = scheduleId;
    }

    public static QuartzJobData of(Integer projectId, Integer scheduleId) {
        return new QuartzJobData(projectId, scheduleId);
    }

    public static QuartzJobData of(JobDataMap jobDataMap) {
        Integer projectId = jobDataMap.getInt(PROJECT_ID);
        Integer scheduleId = jobDataMap.getInt(SCHEDULE_ID);
        return of(projectId, scheduleId);
    }

    public JobDataMap toJobDataMap() {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(PROJECT_ID, projectId);
        jobDataMap.put(SCHEDULE_ID, scheduleId);
        return jobDataMap;
    }

}
