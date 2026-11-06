package io.yak.ops.api.vo;

import io.yak.ops.common.enums.ReleaseState;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncTaskVO {

    private String id;
    private String name;
    private String taskType;
    private ReleaseState status;
    private int definitionVersion;
    private int revision;
    private Integer publishedVersion;
    private int parameterSchemaVersion;
    private JsonNode parameters;
    private String remark;
    private Date createTime;
    private Date updateTime;
    private String updateBy;
}
