package io.yak.ops.business.task.execution;

import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import java.util.List;

/** Manages durable business submissions independently of an execution engine. */
public interface ExecutionService {

    /** Creates an admitted manual run or returns its existing idempotent submission. */
    SubmissionResult submitManual(String definitionId, String requestId, String operatorId);

    /** Reads a Workspace-scoped Task Instance. */
    InstanceEntity instance(String instanceId);

    /** Lists platform submission attempts for a Workspace-scoped Instance. */
    List<AttemptEntity> attempts(String instanceId);
}
