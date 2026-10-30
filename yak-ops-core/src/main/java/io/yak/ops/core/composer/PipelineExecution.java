package io.yak.ops.core.composer;

import io.yak.ops.core.api.common.JobStatus;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletionStage;

/** A process-owned logical submission, including preparation before YakFlow gets a physical Job ID. */
public interface PipelineExecution {

    String getJobId();

    /** Idempotent deferred start; used by the Task scheduler after durable Instance registration. */
    void start();

    JobStatus getStatus();

    /** Completes at terminal outcome; uncertain outcomes complete exceptionally. */
    CompletionStage<JobStatus> completion();

    CompletionStage<Void> cancel();

    OptionalInt getProgressPercent();

    Optional<String> getExecutionReport();

    Optional<String> getExecutionSummary();

    /** The physical YakFlow Job ID can become available after schema preparation. */
    CompletionStage<Optional<String>> engineJobIdReady();

    Optional<String> engineJobId();
}
