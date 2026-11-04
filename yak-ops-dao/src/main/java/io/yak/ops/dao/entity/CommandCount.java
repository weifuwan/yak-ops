package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.CommandType;

import lombok.Data;

@Data
public class CommandCount {

    /**
     * execution state
     */
    private CommandType commandType;

    /**
     * count for state
     */
    private int count;
}
