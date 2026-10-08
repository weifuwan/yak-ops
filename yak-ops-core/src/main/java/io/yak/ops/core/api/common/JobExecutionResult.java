package io.yak.ops.core.api.common;

import java.util.Objects;

/** Successful completion result of one job. Failed/canceled jobs have no success result. */
public final class JobExecutionResult {

    private final JobID jobID;
    private final long netRuntime;

    /** @param netRuntime execution duration in milliseconds, excluding submission preparation */
    public JobExecutionResult(JobID jobID, long netRuntime) {
        this.jobID = Objects.requireNonNull(jobID, "jobID must not be null");
        if (netRuntime < 0) {
            throw new IllegalArgumentException("netRuntime must not be negative");
        }
        this.netRuntime = netRuntime;
    }

    public JobID getJobID() {
        return jobID;
    }

    public long getNetRuntime() {
        return netRuntime;
    }
}
