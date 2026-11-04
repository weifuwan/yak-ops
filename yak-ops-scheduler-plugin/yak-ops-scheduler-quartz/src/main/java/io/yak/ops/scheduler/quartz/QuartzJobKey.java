package io.yak.ops.scheduler.quartz;

import org.quartz.JobKey;

import lombok.Getter;

@Getter
public class QuartzJobKey {

    private final int schedulerId;
    private final int projectId;

    private static final String QUARTZ_JOB_PREFIX = "job";
    private static final String QUARTZ_JOB_GROUP_PREFIX = "jobgroup";
    private static final String UNDERLINE = "_";

    private QuartzJobKey(int projectId, int schedulerId) {
        this.schedulerId = schedulerId;
        this.projectId = projectId;
    }

    public static QuartzJobKey of(int projectId, int schedulerId) {
        return new QuartzJobKey(projectId, schedulerId);
    }

    public JobKey toJobKey() {
        // todo: We don't need to add prefix to job name and job group?
        String jobName = QUARTZ_JOB_PREFIX + UNDERLINE + schedulerId;
        String jobGroup = QUARTZ_JOB_GROUP_PREFIX + UNDERLINE + projectId;
        return new JobKey(jobName, jobGroup);
    }
}
