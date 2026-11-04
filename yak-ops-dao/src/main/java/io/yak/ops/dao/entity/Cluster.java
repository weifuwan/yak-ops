package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_cluster")
public class Cluster {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * cluster code
     */
    private Long code;

    /**
     * cluster name
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
