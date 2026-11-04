package io.yak.ops.extract.master.command;

import io.yak.ops.common.enums.CommandType;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class RunWorkflowCommandParam extends AbstractCommandParam {

    @Override
    public CommandType getCommandType() {
        return CommandType.START_PROCESS;
    }

    public RunWorkflowCommandParam withStartNodes(List<Long> startNodes) {
        this.startNodes = startNodes;
        return this;
    }

}
