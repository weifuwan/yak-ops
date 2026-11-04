package io.yak.ops.api.vo;

import java.util.Date;

import lombok.Data;

@Data
public class AlertPluginInstanceVO {

    private int id;

    private int pluginDefineId;

    /**
     * alert plugin instance name
     */
    private String instanceName;

    /**
     * alert plugin instance type
     */
    private String instanceType;

    /**
     * alert plugin instance warning type
     */
    private String warningType;

    private String pluginInstanceParams;

    private Date createTime;

    private Date updateTime;

    private String alertPluginName;
}
