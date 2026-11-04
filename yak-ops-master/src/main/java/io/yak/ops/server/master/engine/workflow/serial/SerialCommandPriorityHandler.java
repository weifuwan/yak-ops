package io.yak.ops.server.master.engine.workflow.serial;

import io.yak.ops.dao.model.SerialCommandDto;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * This strategy will stop the previous workflow instance and notify the newly workflow instance.
 */
@Slf4j
@Component
public class SerialCommandPriorityHandler extends AbstractSerialCommandHandler {

    @Override
    public void handle(SerialCommandsGroup serialCommandsGroup) {
        final List<SerialCommandDto> serialCommands = serialCommandsGroup.getSerialCommands();
        // Stop the 1 ~ n-1 item
        for (int i = 0; i < serialCommands.size(); i++) {
            final SerialCommandDto serialCommand = serialCommands.get(i);
            if (i == serialCommands.size() - 1) {
                if (serialCommand.getState() == SerialCommandDto.State.WAITING) {
                    launchSerialCommand(serialCommand);
                    log.info("Launched SerialCommand: {}", serialCommand);
                }
                continue;
            }

            if (serialCommand.getState() == SerialCommandDto.State.WAITING) {
                discardSerialCommandAndStopWorkflowInstanceInDB(serialCommand);
                log.info("Discard SerialCommand: {}", serialCommand);
            } else {
                stopWorkflowInstanceInMaster(serialCommand);
                log.info("Stop the pre WorkflowInstance: {} due to the workflow using SERIAL_PRIORITY strategy",
                        serialCommand.getWorkflowInstanceId());
            }
        }
    }
}
