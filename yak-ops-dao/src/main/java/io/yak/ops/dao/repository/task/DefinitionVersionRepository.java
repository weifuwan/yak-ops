package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import java.util.List;
import java.util.Optional;

/** Append-only immutable Definition version persistence. */
public interface DefinitionVersionRepository {

    /**
     * Appends a new immutable Definition parameter snapshot.
     *
     * @param version new snapshot and its owner identity
     * @return the inserted snapshot
     */
    DefinitionVersionEntity append(DefinitionVersionEntity version);

    /**
     * Finds an exact version of a Definition within the owning Workspace.
     *
     * @param workspaceId authorized Workspace identifier
     * @param definitionId stable Definition identifier
     * @param version pinned version number
     * @return matching version snapshot, or empty when absent
     */
    Optional<DefinitionVersionEntity> queryCurrent(String workspaceId, String definitionId, int version);

    /**
     * Lists all immutable versions of a Definition.
     *
     * @param workspaceId authorized Workspace identifier
     * @param definitionId stable Definition identifier
     * @return version snapshots in descending version order
     */
    List<DefinitionVersionEntity> queryByDefinition(String workspaceId, String definitionId);
}
