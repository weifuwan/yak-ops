package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.Flag;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_yak_ops_task_group")
public class TaskGroup implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;

    private long projectCode;
    private String description;
    private int groupSize;
    private int useSize;
    private int userId;
    private Flag status;
    private Date createTime;
    private Date updateTime;

}
