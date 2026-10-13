package io.yak.ops.dao.repository.task;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.time.LocalDateTime;
import java.util.Optional;

/** Single writer of generic Task Definition identity and publication state. */
public interface DefinitionRepository extends BaseRepository<DefinitionEntity> {

    /**
     * Retrieves an active Definition in the specified Workspace.
     *
     * @param workspaceId authorized Workspace identifier
     * @param id stable Definition identifier
     * @return active Definition, or empty when absent or deleted
     */
    Optional<DefinitionEntity> queryById(String workspaceId, String id);

    /**
     * Reads Definition identity for a historical Instance regardless of publication or deletion.
     *
     * @param workspaceId authorized Workspace
     * @param id pinned Definition identifier
     * @return historical Definition in this Workspace, if present
     */
    Optional<DefinitionEntity> queryByIdIncludingDeleted(String workspaceId, String id);

    /**
     * Locks an active Definition for version pinning and publication changes.
     *
     * @param workspaceId authorized Workspace identifier
     * @param id stable Definition identifier
     * @return locked active Definition, or empty when absent or deleted
     */
    Optional<DefinitionEntity> queryByIdForUpdate(String workspaceId, String id);

    /**
     * Finds a Workspace-scoped page of Definitions matching validated filters.
     *
     * @param query Workspace identity, filters, and pagination
     * @return matching Definition page
     */
    PageData<DefinitionEntity> queryPage(DefinitionPageQuery query);

    /**
     * Updates one live Definition without changing its stable identity.
     *
     * @param workspaceId owning Workspace identifier
     * @param entity updated Definition values
     * @return updated entity, or null if no active row was matched
     */
    DefinitionEntity update(String workspaceId, DefinitionEntity entity);

    /**
     * Logically deletes an unpublished Definition while retaining version history.
     *
     * @param workspaceId owning Workspace identifier
     * @param id stable Definition identifier
     * @param at recorded deletion time
     * @param operator actor performing the deletion
     * @return true if an active Definition was marked deleted
     */
    boolean softDelete(String workspaceId, String id, LocalDateTime at, String operator);

    /**
     * Checks whether another live Definition already uses a Workspace-local name.
     *
     * @param workspaceId owning Workspace identifier
     * @param name candidate Definition name
     * @param excludeId current Definition to exclude during rename, or null
     * @return true when another active Definition uses the name
     */
    boolean existsByName(String workspaceId, String name, String excludeId);
}
