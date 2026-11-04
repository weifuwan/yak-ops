package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_environment_worker_group_relation")
public class EnvironmentWorkerGroupRelation {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Long environmentCode;

    /**
     * worker group id
     */
    private String workerGroup;

    /**
     * operator user id
     */
    private Integer operator;

    private Date createTime;

    private Date updateTime;
}
