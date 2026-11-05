package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_yak_ops_workflow_task_lineage")
public class WorkflowTaskLineage {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private long workflowDefinitionCode;
    private int workflowDefinitionVersion;
    private long taskDefinitionCode;
    private int taskDefinitionVersion;
    private long deptProjectCode;
    private long deptWorkflowDefinitionCode;
    private long deptTaskDefinitionCode;
    private Date createTime;
    private Date updateTime;
}
