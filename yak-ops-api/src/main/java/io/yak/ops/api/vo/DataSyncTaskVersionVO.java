package io.yak.ops.api.vo;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;

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
