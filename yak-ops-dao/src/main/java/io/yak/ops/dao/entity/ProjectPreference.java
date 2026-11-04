package io.yak.ops.dao.entity;

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
@TableName("t_ds_project_preference")
public class ProjectPreference {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private long code;

    @TableField("project_code")
    private long projectCode;

    @TableField("preferences")
    private String preferences;

    @TableField("user_id")
    private Integer userId;

    @TableField("state")
    private int state;

    private Date createTime;

    private Date updateTime;
}
