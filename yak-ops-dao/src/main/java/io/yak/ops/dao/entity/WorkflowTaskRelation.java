package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.ConditionType;
import io.yak.ops.common.utils.JSONUtils;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_yak_ops_workflow_task_relation")
public class WorkflowTaskRelation {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String name;

    private int workflowDefinitionVersion;

    private long projectCode;

    private long workflowDefinitionCode;

    private long preTaskCode;

    private int preTaskVersion;

    private long postTaskCode;

    private int postTaskVersion;

    @Deprecated
    private ConditionType conditionType;

    @JsonDeserialize(using = JSONUtils.JsonDataDeserializer.class)
    @JsonSerialize(using = JSONUtils.JsonDataSerializer.class)
    @Deprecated
    private String conditionParams;

    private Date createTime;

    private Date updateTime;

    public WorkflowTaskRelation(String name,
                                int workflowDefinitionVersion,
                                long projectCode,
                                long workflowDefinitionCode,
                                long preTaskCode,
                                int preTaskVersion,
                                long postTaskCode,
                                int postTaskVersion,
                                ConditionType conditionType,
                                String conditionParams) {
        this.name = name;
        this.workflowDefinitionVersion = workflowDefinitionVersion;
        this.projectCode = projectCode;
        this.workflowDefinitionCode = workflowDefinitionCode;
        this.preTaskCode = preTaskCode;
        this.preTaskVersion = preTaskVersion;
        this.postTaskCode = postTaskCode;
        this.postTaskVersion = postTaskVersion;
        this.conditionType = conditionType;
        this.conditionParams = conditionParams;

        Date now = new Date();
        this.createTime = now;
        this.updateTime = now;
    }

    public WorkflowTaskRelation(WorkflowTaskRelationLog processTaskRelationLog) {
        this.name = processTaskRelationLog.getName();
        this.workflowDefinitionVersion = processTaskRelationLog.getWorkflowDefinitionVersion();
        this.projectCode = processTaskRelationLog.getProjectCode();
        this.workflowDefinitionCode = processTaskRelationLog.getWorkflowDefinitionCode();
        this.preTaskCode = processTaskRelationLog.getPreTaskCode();
        this.preTaskVersion = processTaskRelationLog.getPreTaskVersion();
        this.postTaskCode = processTaskRelationLog.getPostTaskCode();
        this.postTaskVersion = processTaskRelationLog.getPostTaskVersion();
        this.conditionType = processTaskRelationLog.getConditionType();
        this.conditionParams = processTaskRelationLog.getConditionParams();

        this.createTime = processTaskRelationLog.getCreateTime();
        this.updateTime = new Date();
    }

}
