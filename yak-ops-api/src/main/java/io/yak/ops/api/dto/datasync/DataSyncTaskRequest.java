package io.yak.ops.api.dto.datasync;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class DataSyncTaskRequest {
    private String name;
    private String remark;
    private String taskType;
    private boolean debug;
    private Integer expectedRevision;
    private JsonNode parameters;
}
