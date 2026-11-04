package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.WorkflowInstanceRelation;
import io.yak.ops.dao.mapper.WorkflowInstanceRelationMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkflowInstanceMapDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

/**
 * Process Instance Map Dao implementation
 */
@Repository
public class WorkflowInstanceMapDaoImpl extends BaseDao<WorkflowInstanceRelation, WorkflowInstanceRelationMapper>
        implements
            WorkflowInstanceMapDao {

    public WorkflowInstanceMapDaoImpl(@NonNull WorkflowInstanceRelationMapper workflowInstanceRelationMapper) {
        super(workflowInstanceRelationMapper);
    }

    @Override
    public WorkflowInstanceRelation queryWorkflowMapByParent(Integer parentWorkflowId, Integer parentTaskId) {
        return mybatisMapper.queryByParentId(parentWorkflowId, parentTaskId);
    }

    @Override
    public List<Integer> querySubWorkflowInstanceIds(int workflowInstanceId) {
        return mybatisMapper.querySubIdListByParentId(workflowInstanceId);
    }

    @Override
    public void deleteByParentId(int workflowInstanceId) {
        mybatisMapper.deleteByParentId(workflowInstanceId);
    }
}
