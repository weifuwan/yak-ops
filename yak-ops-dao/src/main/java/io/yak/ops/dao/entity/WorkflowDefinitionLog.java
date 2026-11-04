package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_workflow_definition_log")
public class WorkflowDefinitionLog extends WorkflowDefinition {

    private int operator;

    private Date operateTime;

    public WorkflowDefinitionLog() {
        super();
    }

    public WorkflowDefinitionLog(WorkflowDefinition workflowDefinition) {
        this.setCode(workflowDefinition.getCode());
        this.setName(workflowDefinition.getName());
        this.setVersion(workflowDefinition.getVersion());
        this.setReleaseState(workflowDefinition.getReleaseState());
        this.setProjectCode(workflowDefinition.getProjectCode());
        this.setDescription(workflowDefinition.getDescription());
        this.setGlobalParams(workflowDefinition.getGlobalParams());
        this.setGlobalParamList(workflowDefinition.getGlobalParamList());
        this.setGlobalParamMap(workflowDefinition.getGlobalParamMap());
        this.setCreateTime(workflowDefinition.getCreateTime());
        this.setUpdateTime(workflowDefinition.getUpdateTime());
        this.setFlag(workflowDefinition.getFlag());
        this.setUserId(workflowDefinition.getUserId());
        this.setUserName(workflowDefinition.getUserName());
        this.setProjectName(workflowDefinition.getProjectName());
        this.setLocations(workflowDefinition.getLocations());
        this.setScheduleReleaseState(workflowDefinition.getScheduleReleaseState());
        this.setTimeout(workflowDefinition.getTimeout());
        this.setModifyBy(workflowDefinition.getModifyBy());
        this.setWarningGroupId(workflowDefinition.getWarningGroupId());
        this.setExecutionType(workflowDefinition.getExecutionType());
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

}
