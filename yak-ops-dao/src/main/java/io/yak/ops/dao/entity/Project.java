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
@TableName("t_yak_ops_project")
public class Project {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("user_id")
    private Integer userId;

    @TableField(exist = false)
    private String userName;

    /**
     * project code
     */
    private long code;

    /**
     * project name
     */
    private String name;

    /**
     * project description
     */
    private String description;

    private Date createTime;

    private Date updateTime;

    /**
     * permission
     */
    @TableField(exist = false)
    private int perm;

    /**
     * process define count
     */
    @TableField(exist = false)
    private int defCount;

}
