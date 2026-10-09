package io.yak.ops.core.execution;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import java.util.concurrent.CompletableFuture;

/** Control and result handle for one submitted batch or streaming job. */
public interface JobClient {

    /** Returns the identity assigned to this job submission. */
    JobID getJobID();

    /** Returns the job's current runtime lifecycle status. */
    CompletableFuture<JobStatus> getJobStatus();

    /**
    * Requests cancellation of this job.
    *
    * @return a future completed when cancellation reaches a terminal state,
    *         or completed exceptionally if cancellation fails
    */
    CompletableFuture<Void> cancel();

    /**
    * Returns the execution result only after successful completion.
    *
    * @return a future completed normally only for a finished job, and exceptionally
    *         for a failed or canceled job
    */
    CompletableFuture<JobExecutionResult> getJobExecutionResult();
}
