package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_relation_sub_workflow")
public class RelationSubWorkflow {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * parent process instance id
     */
    private Long parentWorkflowInstanceId;

    /**
     * parent task instance id
     */
    private Long parentTaskCode;

    /**
     * process instance id
     */
    private Long subWorkflowInstanceId;
}
