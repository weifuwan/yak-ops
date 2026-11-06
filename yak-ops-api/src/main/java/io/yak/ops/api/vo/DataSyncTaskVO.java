package io.yak.ops.api.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncTaskVO {
    private String id;
    private String name;
    private String taskType;
    private String status;
    private int definitionVersion;
    private int revision;
    private Integer publishedVersion;
    private Boolean publishedDebug;
    private boolean hasUnpublishedChanges;
    private int parameterSchemaVersion;
    private JsonNode parameters;
    private boolean debug;
    private String remark;
    private Date createTime;
    private Date updateTime;
    private String updateBy;
}
