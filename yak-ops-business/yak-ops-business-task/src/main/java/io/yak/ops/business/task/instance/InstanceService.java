package io.yak.ops.business.task.instance;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import java.util.List;

/** Queries persisted Task Instances and their physical submission attempts within a Workspace. */
public interface InstanceService {

    /**
     * Returns one Workspace-owned Instance.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId logical Task Instance identity
     * @return persisted Instance
     */
    InstanceEntity instance(String workspaceId, String instanceId);

    /**
     * Returns physical submission attempts for an authorized Instance.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId logical Task Instance identity
     * @return Attempts ordered by submission number
     */
    List<AttemptEntity> attempts(String workspaceId, String instanceId);

    /**
     * Returns recent Workspace-owned Instances.
     *
     * @param workspaceId authorized Workspace
     * @param limit maximum result count
     * @return recent Instances
     */
    List<InstanceEntity> recent(String workspaceId, int limit);

    /**
     * Pages Workspace-owned Instances.
     *
     * @param workspaceId authorized Workspace
     * @param pageNo one-based page number
     * @param pageSize maximum page size up to 200
     * @return ordered page of persisted Instances
     */
    PageData<InstanceEntity> page(String workspaceId, long pageNo, long pageSize);
}
