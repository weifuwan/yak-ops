package io.yak.ops.server.master.engine.command.handler;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.extract.master.command.RunWorkflowCommandParam;

import org.springframework.stereotype.Component;

/**
 * Used to handle the {@link CommandType#COMPLEMENT_DATA} which will start the workflow definition.
 * <p> You can specify the start nodes at {@link RunWorkflowCommandParam}
 */
@Component
public class BackfillWorkflowCommandHandler extends RunWorkflowCommandHandler {

    @Override
    public CommandType commandType() {
        return CommandType.COMPLEMENT_DATA;
    }
}
