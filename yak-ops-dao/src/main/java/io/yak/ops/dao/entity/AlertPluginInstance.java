package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_alert_plugin_instance")
public class AlertPluginInstance {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "plugin_define_id", updateStrategy = FieldStrategy.NEVER)
    private int pluginDefineId;

    /**
     * alert plugin instance name
     */
    @TableField("instance_name")
    private String instanceName;

    @TableField("plugin_instance_params")
    private String pluginInstanceParams;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    public AlertPluginInstance() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    public AlertPluginInstance(int pluginDefineId, String pluginInstanceParams, String instanceName) {
        this.pluginDefineId = pluginDefineId;
        this.pluginInstanceParams = pluginInstanceParams;
        this.createTime = new Date();
        this.updateTime = new Date();
        this.instanceName = instanceName;
    }

    public AlertPluginInstance(int id, String pluginInstanceParams, String instanceName,
                               Date updateDate) {
        this.id = id;
        this.pluginInstanceParams = pluginInstanceParams;
        this.updateTime = updateDate;
        this.instanceName = instanceName;
    }
}
