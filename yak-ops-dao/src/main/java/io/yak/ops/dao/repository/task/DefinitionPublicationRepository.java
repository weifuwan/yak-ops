package io.yak.ops.dao.repository.task;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.DefinitionPublicationEntity;
import java.util.Optional;

/** Workspace-scoped append-only publication history and request deduplication. */
public interface DefinitionPublicationRepository {
    /**
     * Finds a successful publication for a caller request.
     * @param workspaceId authorized Workspace
     * @param definitionId owning task
     * @param requestId case-sensitive caller request key
     * @return successful receipt, or empty if no publication committed
     */
    Optional<DefinitionPublicationEntity> findByRequest(String workspaceId, String definitionId, String requestId);

    /**
     * Adds a successful receipt in the transaction changing the task's published state.
     * @param publication initialized immutable receipt
     */
    void append(DefinitionPublicationEntity publication);

    /**
     * Reads a bounded history page ordered by commit audit time and identifier.
     * @param workspaceId authorized Workspace
     * @param definitionId owning task
     * @param pageNo one-based page number
     * @param pageSize validated page size
     * @return successful publications in descending order
     */
    PageData<DefinitionPublicationEntity> queryPage(String workspaceId, String definitionId, int pageNo, int pageSize);
}
