package io.yak.ops.extract.master.command;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class WorkflowFailoverCommandParam extends AbstractCommandParam {

    /**
     * The workflow instance state before failover, we need to set the workflow instance state to this value after recover.
     */
    private WorkflowExecutionStatus workflowExecutionStatus;

    @Override
    public CommandType getCommandType() {
        return CommandType.RECOVER_TOLERANCE_FAULT_PROCESS;
    }
}
