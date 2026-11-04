package io.yak.ops.api.dto;

import java.util.Date;

import lombok.Data;

@Data
public class AuditDto {

    private String userName;

    private String modelType;

    private String modelName;

    private String operation;

    private Date createTime;

    private String description;

    private String detail;

    private String latency;
}
