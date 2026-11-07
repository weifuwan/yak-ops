package io.yak.ops.api.vo;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataSyncTaskVersionVO {

    private String definitionId;
    private int version;
    private String createBy;
    private Date createTime;
    private JsonNode parameters;
}
