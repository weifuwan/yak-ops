package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_relation_project_worker_group")
public class ProjectWorkerGroup {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Long projectCode;

    private String workerGroup;

    private Date createTime;

    private Date updateTime;
}
