package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.TaskGroupQueueStatus;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_yak_ops_task_group_queue")
public class TaskGroupQueue implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private int taskId;

    private String taskName;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String projectCode;

    @TableField(exist = false)
    private String workflowInstanceName;

    private int groupId;

    private Integer workflowInstanceId;

    private int priority;

    /**
     * is force start
     * 0 NO ,1 YES
     */
    private int forceStart;

    /**
     * ready to get the queue by other task finish
     * 0 NO ,1 YES
     */
    private int inQueue;

    private TaskGroupQueueStatus status;

    private Date createTime;

    private Date updateTime;
}
