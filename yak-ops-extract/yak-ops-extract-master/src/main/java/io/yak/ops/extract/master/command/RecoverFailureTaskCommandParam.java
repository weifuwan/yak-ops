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
public class RecoverFailureTaskCommandParam extends AbstractCommandParam {

    @Override
    public CommandType getCommandType() {
        return CommandType.START_FAILURE_TASK_PROCESS;
    }
}
