package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_plugin_define")
public class PluginDefine {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("plugin_name")
    private String pluginName;

    @TableField("plugin_type")
    private String pluginType;

    @TableField("plugin_params")
    private String pluginParams;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    public PluginDefine(String pluginName, String pluginType, String pluginParams) {
        this.pluginName = pluginName;
        this.pluginType = pluginType;
        this.pluginParams = pluginParams;
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
