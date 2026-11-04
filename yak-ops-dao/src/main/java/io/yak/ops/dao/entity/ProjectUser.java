package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_relation_project_user")
public class ProjectUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("user_id")
    private int userId;

    @TableField("project_id")
    private int projectId;

    @TableField(exist = false)
    private long projectCode;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String userName;

    /**
     * permission
     */
    private int perm;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
