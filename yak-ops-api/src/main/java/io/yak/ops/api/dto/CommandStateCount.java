package io.yak.ops.api.dto;

import io.yak.ops.common.enums.CommandType;

import lombok.Data;

@Data
public class CommandStateCount {

    private int errorCount;
    private int normalCount;
    private CommandType commandState;

    public CommandStateCount() {
    }

    public CommandStateCount(int errorCount, int normalCount, CommandType commandState) {
        this.errorCount = errorCount;
        this.normalCount = normalCount;
        this.commandState = commandState;
    }

}
