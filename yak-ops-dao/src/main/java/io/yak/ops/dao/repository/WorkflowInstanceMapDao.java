package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.WorkflowInstanceRelation;

import java.util.List;

public interface WorkflowInstanceMapDao extends IDao<WorkflowInstanceRelation> {

    /**
     * find work workflow map by parent workflow id and parent task id.
     * @param parentWorkflowId parentWorkWorkflowId
     * @param parentTaskId        parentTaskId
     * @return workflow instance map
     */
    WorkflowInstanceRelation queryWorkflowMapByParent(Integer parentWorkflowId, Integer parentTaskId);

    List<Integer> querySubWorkflowInstanceIds(int workflowInstanceId);

    void deleteByParentId(int workflowInstanceId);
}
