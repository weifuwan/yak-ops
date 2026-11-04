package io.yak.ops.server.master.engine.workflow.serial;

import io.yak.ops.dao.model.SerialCommandDto;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * This strategy will discard the new workflow instance if there is a running workflow instance.
 */
@Slf4j
@Component
public class SerialCommandDiscardHandler extends AbstractSerialCommandHandler {

    @Override
    public void handle(SerialCommandsGroup serialCommandsGroup) {
        // If the first item in the queue is not running, then notify it to run.
        // Discard all other items in the queue.
        List<SerialCommandDto> serialCommands = serialCommandsGroup.getSerialCommands();
        for (int i = 0; i < serialCommands.size(); i++) {
            SerialCommandDto serialCommand = serialCommands.get(i);
            if (i == 0) {
                if (serialCommand.getState() == SerialCommandDto.State.WAITING) {
                    launchSerialCommand(serialCommand);
                    log.info("Launched SerialCommand: {}", serialCommand);
                }
                continue;
            }
            // Discard all other items in the queue.
            if (serialCommand.getState() != SerialCommandDto.State.WAITING) {
                throw new IllegalStateException(
                        "The post SerialCommand except WAITING state but -> " + serialCommand.getState());
            }
            discardSerialCommandAndStopWorkflowInstanceInDB(serialCommand);
            log.info("Discard SerialCommand: {}", serialCommand);
        }
    }

}
