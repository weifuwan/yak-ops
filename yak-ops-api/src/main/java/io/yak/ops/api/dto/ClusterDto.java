package io.yak.ops.api.dto;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class ClusterDto {

    private int id;

    /**
     * cluster code
     */
    private Long code;

    /**
     * cluster name
     */
    private String name;

    /**
     * config content
     */
    private String config;

    private String description;

    private List<String> workflowDefinitions;

    /**
     * operator user id
     */
    private Integer operator;

    private Date createTime;

    private Date updateTime;
}
