package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import lombok.Data;

/**
 * Common persistence fields for modeling records.
 *
 * <p>projectCode is populated by the MyBatis tenant interceptor from the
 * authenticated Yak Ops workspace, never trusted from a request payload.
 */
@Data
public class ModelingBasePO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    protected String id;

    @TableField("project_code")
    protected Long projectCode;

    protected Date createTime;
    protected String createBy;
    protected Date updateTime;
    protected String updateBy;

    public void initUpdate() {
        this.updateTime = new Date();
    }

    public void initInsert() {
        if (this.id == null || this.id.trim().isEmpty()) {
            this.id = UUID.randomUUID().toString().replace("-", "");
        }
        this.createBy = "sys";
        this.updateBy = "sys";
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    public void initInsertWithNoId() {
        this.createBy = "sys";
        this.updateBy = "sys";
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
