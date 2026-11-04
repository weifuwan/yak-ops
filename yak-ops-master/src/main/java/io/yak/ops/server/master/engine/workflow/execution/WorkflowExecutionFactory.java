package io.yak.ops.server.master.engine.workflow.execution;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.repository.CommandDao;
import io.yak.ops.server.master.engine.command.ICommandHandler;
import io.yak.ops.server.master.engine.exceptions.CommandDuplicateHandleException;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowExecutionFactory {

    @Autowired
    private List<ICommandHandler> commandHandlers;

    @Autowired
    private CommandDao commandDao;

    /**
     * Generate WorkflowExecution from command.
     * <p> We use transaction here to make sure that the command will be handled only once. Since in some case if the
     * master cluster is reblancing, the master slot might different in different master.
     */
    @Transactional
    public IWorkflowExecution createWorkflowExecuteRunnable(Command command) {
        deleteCommandOrThrow(command);
        return doCreateWorkflowExecution(command);
    }

    /**
     * Create WorkflowExecution from command.
     * <p> Each WorkflowExecution represent a workflow instance, so this method might create workflow instance in db, dependents on the command type.
     */
    private IWorkflowExecution doCreateWorkflowExecution(Command command) {
        final CommandType commandType = command.getCommandType();
        final ICommandHandler commandHandler = commandHandlers
                .stream()
                .filter(c -> c.commandType() == commandType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot find ICommandHandler for commandType: " + commandType));
        return commandHandler.handleCommand(command);
    }

    /**
     * Delete the command in db, if the command is not exist in db will throw CommandDuplicateHandleException
     */
    private void deleteCommandOrThrow(Command command) {
        boolean deleteResult = commandDao.deleteById(command.getId());
        if (!deleteResult) {
            throw new CommandDuplicateHandleException(command);
        }
    }
}
