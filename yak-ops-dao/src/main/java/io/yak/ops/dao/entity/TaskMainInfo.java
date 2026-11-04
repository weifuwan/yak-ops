package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.ReleaseState;

import java.util.Date;
import java.util.Map;

import lombok.Data;

@Data
public class TaskMainInfo {

    private long id;

    private String taskName;

    private long taskCode;

    private int taskVersion;

    private String taskType;

    /**
     * create time
     */
    private Date taskCreateTime;

    /**
     * update time
     */
    private Date taskUpdateTime;

    private long projectCode;

    private long processDefinitionCode;

    private int processDefinitionVersion;

    private String processDefinitionName;

    private ReleaseState processReleaseState;

    /**
     * upstreamTaskMap(k:code,v:name)
     */
    private Map<Long, String> upstreamTaskMap;

    private long upstreamTaskCode;

    private String upstreamTaskName;
}
