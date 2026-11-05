package io.yak.ops.dao.entity;

import java.util.Date;

import org.springframework.beans.BeanUtils;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_audit_log")
public class AuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String modelType;

    private String operationType;

    private Long modelId;

    @TableField(exist = false)
    private String userName;

    /**
     * operation time
     */
    private Date createTime;

    private String detail;

    private String description;

    private String modelName;

    private long latency;

    public static AuditLog copyNewOne(AuditLog auditLog) {
        AuditLog auditLogNew = new AuditLog();
        BeanUtils.copyProperties(auditLog, auditLogNew);
        return auditLogNew;
    }
}
