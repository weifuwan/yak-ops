package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_environment")
public class Environment {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * environment code
     */
    private Long code;

    /**
     * environment name
     */
    private String name;

    /**
     * config content
     */
    private String config;

    private String description;

    /**
     * operator user id
     */
    private Integer operator;

    private Date createTime;

    private Date updateTime;
}
