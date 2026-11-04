package io.yak.ops.server.master.engine.command;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.server.master.engine.command.handler.ReRunWorkflowCommandHandler;
import io.yak.ops.server.master.engine.command.handler.RecoverFailureTaskCommandHandler;
import io.yak.ops.server.master.engine.command.handler.RunWorkflowCommandHandler;
import io.yak.ops.server.master.engine.workflow.execution.WorkflowExecution;

/**
 * The interface represent the handler used to handle the {@link Command}.
 * <p> Each handler should handle a specific type of command.
 *
 * @see RunWorkflowCommandHandler
 * @see ReRunWorkflowCommandHandler
 * @see RecoverFailureTaskCommandHandler
 */
public interface ICommandHandler {

    /**
     * Handle the command and return the WorkflowExecution.
     */
    WorkflowExecution handleCommand(final Command command);

    /**
     * The type of the command which should be handled by this handler.
     */
    CommandType commandType();

}
