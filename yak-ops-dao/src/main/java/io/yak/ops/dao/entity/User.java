package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.UserType;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
@TableName("t_ds_user")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String userName;

    @JsonIgnore
    private String userPassword;

    private String email;

    private String phone;

    private UserType userType;

    private int tenantId;

    private int state;

    @TableField(exist = false)
    private String tenantCode;

    @TableField(exist = false)
    private String queueName;

    @TableField(exist = false)
    private String alertGroup;

    private String queue;

    private String timeZone;

    private Date createTime;

    private Date updateTime;

}
