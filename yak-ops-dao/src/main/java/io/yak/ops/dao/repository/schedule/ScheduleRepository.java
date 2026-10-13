package io.yak.ops.dao.repository.schedule;

import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import java.time.LocalDateTime;
import java.util.Optional;

/** Workspace-scoped schedule persistence; mutations follow the Definition-before-Schedule lock order. */
public interface ScheduleRepository {
    /**
     * Inserts a new disabled configuration; the database enforces target uniqueness.
     * @param entity initialized schedule with an explicit Workspace and audit identity
     */
    void add(ScheduleEntity entity);

    /**
     * Reads a live schedule without locking it.
     * @param workspaceId authorized Workspace
     * @param id schedule identifier
     * @return live row or empty
     */
    Optional<ScheduleEntity> queryById(String workspaceId, String id);

    /**
     * Locks a live schedule after its Definition has been locked by the caller.
     * @param workspaceId authorized Workspace
     * @param id schedule identifier
     * @return locked live row or empty
     */
    Optional<ScheduleEntity> queryByIdForUpdate(String workspaceId, String id);

    /**
     * Looks up the sole live schedule for a target.
     * @param workspaceId authorized Workspace
     * @param targetType root target category
     * @param targetId immutable target identifier
     * @return live configuration or empty
     */
    Optional<ScheduleEntity> queryByTarget(String workspaceId, ScheduleTargetType targetType, String targetId);

    /**
     * Returns summaries in stable update-time and ID order without loading target parameters.
     * @param query validated Workspace selection
     * @return bounded schedule page
     */
    PageData<ScheduleEntity> queryPage(SchedulePageQuery query);

    /**
     * Updates only the time rule and audit fields of a disabled live row.
     * @param entity replacement time-rule and incremented revision
     * @param expectedRevision locked revision used for optimistic conflict detection
     * @return true when the expected disabled row was updated
     */
    boolean updateDisabled(ScheduleEntity entity, long expectedRevision);

    /**
     * Disables and logically deletes a configuration without removing history.
     * @param workspaceId authorized Workspace
     * @param id schedule identifier
     * @param expectedRevision current locked revision
     * @param at deletion and audit time
     * @param operator actor requesting deletion
     * @return true when the expected live row was deleted
     */
    boolean softDelete(String workspaceId, String id, long expectedRevision, LocalDateTime at, String operator);

    /**
     * Stops the target's enabled configuration in its publication transaction.
     * @param workspaceId owning Workspace
     * @param targetType target category
     * @param targetId Definition locked by the caller
     * @param at audit time
     * @param operator actor changing publication
     */
    void disableForTarget(
            String workspaceId, ScheduleTargetType targetType, String targetId, LocalDateTime at, String operator);
}
