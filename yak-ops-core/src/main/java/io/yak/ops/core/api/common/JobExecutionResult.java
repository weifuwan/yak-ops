package io.yak.ops.core.api.common;

import java.util.Objects;

/** Result of a successfully finished job; failed and canceled jobs have no successful result. */
public final class JobExecutionResult {

    private final JobID jobID;
    private final long netRuntime;

    /**
     * Creates a successful execution result.
     *
     * @param jobID the completed job's identity
     * @param netRuntime execution time in milliseconds, excluding submission preparation
     */
    public JobExecutionResult(JobID jobID, long netRuntime) {
        this.jobID = Objects.requireNonNull(jobID, "jobID must not be null");
        if (netRuntime < 0) {
            throw new IllegalArgumentException("netRuntime must not be negative");
        }
        this.netRuntime = netRuntime;
    }

    /** Returns the identity of the successfully finished job. */
    public JobID getJobID() {
        return jobID;
    }

    /** Returns the net execution time in milliseconds. */
    public long getNetRuntime() {
        return netRuntime;
    }
}
