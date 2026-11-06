package io.yak.ops.api.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncTaskVersionVO {
    private String definitionId;
    private int version;
    private int parameterSchemaVersion;
    private String createBy;
    private Date createTime;
    private JsonNode parameters;
}
