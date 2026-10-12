package io.yak.ops.business.task.execution;

/** Owns atomic admission of one authorized Task execution request. */
public interface AdmissionService {

    /**
     * Pins a published Definition version, persists its first Attempt, and acquires all
     * exclusive resource claims in one transaction before physical execution.
     *
     * @param request validated Task identity, parameters and read-only execution plan
     * @return newly admitted run or the original idempotent submission
     */
    SubmissionResult admit(AdmissionRequest request);
}
