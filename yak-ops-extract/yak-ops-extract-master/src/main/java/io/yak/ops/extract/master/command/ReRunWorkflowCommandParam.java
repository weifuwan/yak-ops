package io.yak.ops.extract.master.command;

import io.yak.ops.common.enums.CommandType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class ReRunWorkflowCommandParam extends AbstractCommandParam {

    private Integer workflowInstanceId;

    @Override
    public CommandType getCommandType() {
        return CommandType.REPEAT_RUNNING;
    }
}
