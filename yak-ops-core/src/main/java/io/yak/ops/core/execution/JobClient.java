package io.yak.ops.core.execution;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import java.util.concurrent.CompletableFuture;

/** Client scoped to one submitted batch or streaming job. */
public interface JobClient {

    JobID getJobID();

    CompletableFuture<JobStatus> getJobStatus();

    /** Completes when cancellation has reached a terminal state or fails. */
    CompletableFuture<Void> cancel();

    /** Completes normally only for FINISHED; fails for FAILED or CANCELED. */
    CompletableFuture<JobExecutionResult> getJobExecutionResult();
}
