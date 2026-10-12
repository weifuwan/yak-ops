package io.yak.ops.business.task.execution;

/** Manages durable business submissions independently of an execution engine. */
public interface ExecutionService {

    /** Creates an admitted manual run or returns its existing idempotent submission. */
    SubmissionResult submitManual(String definitionId, String requestId, String operatorId);
}
