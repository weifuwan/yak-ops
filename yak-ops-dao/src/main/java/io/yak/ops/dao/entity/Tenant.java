package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_tenant")
public class Tenant {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String tenantCode;

    private String description;

    private int queueId;

    @TableField(exist = false)
    private String queueName;

    @TableField(exist = false)
    private String queue;

    private Date createTime;
    private Date updateTime;

    public Tenant() {
    }

    public Tenant(String tenantCode, String description, int queueId) {
        Date now = new Date();
        this.tenantCode = tenantCode;
        this.description = description;
        this.queueId = queueId;
        this.createTime = now;
        this.updateTime = now;
    }

    public Tenant(int id, String tenantCode, String description, int queueId) {
        Date now = new Date();
        this.id = id;
        this.tenantCode = tenantCode;
        this.description = description;
        this.queueId = queueId;
        this.createTime = now;
        this.updateTime = now;
    }

}
