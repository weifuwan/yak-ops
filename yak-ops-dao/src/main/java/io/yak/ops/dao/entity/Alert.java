package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.enums.AlertType;
import io.yak.ops.common.enums.WarningType;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
@TableName("t_ds_alert")
public class Alert {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "sign")
    private String sign;

    @TableField(value = "title")
    private String title;

    @TableField(value = "content")
    private String content;

    @TableField(value = "alert_status")
    private AlertStatus alertStatus;

    @TableField(value = "warning_type")
    private WarningType warningType;

    @TableField(value = "log")
    private String log;

    @TableField("alertgroup_id")
    private Integer alertGroupId;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    @TableField("project_code")
    private Long projectCode;

    @TableField("workflow_definition_code")
    private Long workflowDefinitionCode;

    @TableField("workflow_instance_id")
    private Integer workflowInstanceId;

    @TableField("alert_type")
    private AlertType alertType;

    @TableField(exist = false)
    private Map<String, Object> info = new HashMap<>();

}
