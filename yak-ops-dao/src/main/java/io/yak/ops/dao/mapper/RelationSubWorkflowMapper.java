package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.RelationSubWorkflow;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface RelationSubWorkflowMapper extends BaseMapper<RelationSubWorkflow> {

    int batchInsert(@Param("relationSubWorkflows") List<RelationSubWorkflow> relationSubWorkflows);

    RelationSubWorkflow queryParentWorkflowInstance(@Param("subWorkflowInstanceId") Long subWorkflowInstanceId);

}
