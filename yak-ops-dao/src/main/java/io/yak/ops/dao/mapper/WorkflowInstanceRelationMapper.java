package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.WorkflowInstanceRelation;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * workflow instance map mapper interface
 */
public interface WorkflowInstanceRelationMapper extends BaseMapper<WorkflowInstanceRelation> {

    /**
     * query workflow instance by parentId
     * @param parentWorkflowInstanceId parentWorkflowInstanceId
     * @param parentTaskId parentTaskId
     * @return workflow instance map
     */
    WorkflowInstanceRelation queryByParentId(@Param("parentWorkflowInstanceId") int parentWorkflowInstanceId,
                                             @Param("parentTaskId") int parentTaskId);

    /**
     * query by sub process id
     * @param subWorkflowInstanceId subWorkflowInstanceId
     * @return workflow instance map
     */
    WorkflowInstanceRelation queryBySubWorkflowId(@Param("subWorkflowInstanceId") Integer subWorkflowInstanceId);

    /**
     * delete by parent process id
     * @param parentWorkflowInstanceId parentWorkflowInstanceId
     * @return delete result
     */
    int deleteByParentWorkflowInstanceId(@Param("parentWorkflowInstanceId") int parentWorkflowInstanceId);

    /**
     *  query sub workflow instance  ids by parent instance id
     * @param parentInstanceId parentInstanceId
     * @return sub workflow instance ids
     */
    List<Integer> querySubIdListByParentId(@Param("parentInstanceId") int parentInstanceId);

    void deleteByParentId(@Param("parentWorkflowInstanceId") int workflowInstanceId);
}
