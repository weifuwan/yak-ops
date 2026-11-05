package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.AlertStatus;

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
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_yak_ops_alert_send_status")
public class AlertSendStatus {

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "alert_id")
    private int alertId;

    @TableField(value = "alert_plugin_instance_id")
    private int alertPluginInstanceId;

    /**
     * alert send status
     */
    @TableField(value = "send_status")
    private AlertStatus sendStatus;

    @TableField(value = "log")
    private String log;

    @TableField("create_time")
    private Date createTime;

}
