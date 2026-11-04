package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface WorkflowTaskRelationLogMapper extends BaseMapper<WorkflowTaskRelationLog> {

    /**
     * query workflow task relation log
     *
     * @param workflowDefinitionCode workflow definition code
     * @param workflowDefinitionVersion workflow version
     * @return workflow task relation log
     */
    List<WorkflowTaskRelationLog> queryByWorkflowCodeAndVersion(@Param("workflowDefinitionCode") long workflowDefinitionCode,
                                                                @Param("workflowDefinitionVersion") int workflowDefinitionVersion);

    /**
     * batch insert workflow task relation
     *
     * @param taskRelationList taskRelationList
     * @return int
     */
    int batchInsert(@Param("taskRelationList") List<WorkflowTaskRelationLog> taskRelationList);

    /**
     * delete workflow task relation log by workflowDefinitionCode and version
     *
     * @param workflowDefinitionCode workflow definition code
     * @param workflowDefinitionVersion workflow version
     * @return int
     */
    int deleteByCode(@Param("workflowDefinitionCode") long workflowDefinitionCode,
                     @Param("workflowDefinitionVersion") int workflowDefinitionVersion);

    /**
     * delete workflow task relation
     *
     * @param workflowTaskRelationLog  workflowTaskRelationLog
     * @return int
     */
    int deleteRelation(@Param("workflowTaskRelationLog") WorkflowTaskRelationLog workflowTaskRelationLog);

    /**
     * query workflow task relation log
     *
     * @param workflowTaskRelation workflowTaskRelation
     * @return workflow task relation log
     */
    WorkflowTaskRelationLog queryRelationLogByRelation(@Param("workflowTaskRelation") WorkflowTaskRelation workflowTaskRelation);

    List<WorkflowTaskRelationLog> queryByWorkflowDefinitionCode(@Param("workflowDefinitionCode") long workflowDefinitionCode);

    void deleteByWorkflowDefinitionCode(@Param("workflowDefinitionCode") long workflowDefinitionCode);
}
