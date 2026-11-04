package io.yak.ops.api.dto;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class EnvironmentDto {

    private Integer id;

    /**
     * environment code
     */
    private Long code;

    /**
     * environment name
     */
    private String name;

    /**
     * config content
     */
    private String config;

    private String description;

    private List<String> workerGroups;

    /**
     * operator user id
     */
    private Integer operator;

    private Date createTime;

    private Date updateTime;
}
