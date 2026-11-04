package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_workflow_task_relation_log")
public class WorkflowTaskRelationLog extends WorkflowTaskRelation {

    private int operator;

    private Date operateTime;

    public WorkflowTaskRelationLog() {
        super();
    }

    public WorkflowTaskRelationLog(WorkflowTaskRelation workflowTaskRelation) {
        super();
        this.setName(workflowTaskRelation.getName());
        this.setWorkflowDefinitionCode(workflowTaskRelation.getWorkflowDefinitionCode());
        this.setWorkflowDefinitionVersion(workflowTaskRelation.getWorkflowDefinitionVersion());
        this.setProjectCode(workflowTaskRelation.getProjectCode());
        this.setPreTaskCode(workflowTaskRelation.getPreTaskCode());
        this.setPreTaskVersion(workflowTaskRelation.getPreTaskVersion());
        this.setPostTaskCode(workflowTaskRelation.getPostTaskCode());
        this.setPostTaskVersion(workflowTaskRelation.getPostTaskVersion());
        this.setConditionType(workflowTaskRelation.getConditionType());
        this.setConditionParams(workflowTaskRelation.getConditionParams());
        this.setCreateTime(workflowTaskRelation.getCreateTime());
        this.setUpdateTime(workflowTaskRelation.getUpdateTime());
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
