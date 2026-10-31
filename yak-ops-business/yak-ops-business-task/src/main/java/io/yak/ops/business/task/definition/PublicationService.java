package io.yak.ops.business.task.definition;

import io.yak.ops.dao.entity.task.DefinitionEntity;

/** Publishes a previously validated Task Definition version in one short transaction. */
public interface PublicationService {

    /**
     * Atomically publishes a validated snapshot in the current Workspace, deduplicating successful retries.
     *
     * @param id stable Definition identifier
     * @param version immutable parameter version validated before the transaction
     * @param expectedRevision task revision read before validation
     * @param requestId caller key reused only for the identical version and edit revision
     * @param debug saved diagnostics captured at the expected edit revision
     * @param schedulable whether the validated parameters support periodic execution
     * @param operatorId publishing operator
     * @return published Definition identity and status
     */
    DefinitionEntity publish(
            String id,
            int version,
            long expectedRevision,
            String requestId,
            boolean debug,
            boolean schedulable,
            String operatorId);
}
