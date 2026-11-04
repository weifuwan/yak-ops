package io.yak.ops.server.master.engine.workflow.serial;

import io.yak.ops.dao.model.SerialCommandDto;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * This strategy will wait the oldest workflow instance.
 */
@Slf4j
@Component
public class SerialCommandWaitHandler extends AbstractSerialCommandHandler {

    @Override
    public void handle(SerialCommandsGroup serialCommandsGroup) {
        // If the first command is not fired, then fire it.
        final List<SerialCommandDto> serialCommands = serialCommandsGroup.getSerialCommands();
        if (CollectionUtils.isEmpty(serialCommands)) {
            return;
        }
        final SerialCommandDto serialCommand = serialCommands.get(0);
        if (serialCommand.getState() == SerialCommandDto.State.WAITING) {
            launchSerialCommand(serialCommand);
            log.info("Launched SerialCommand: {}", serialCommand);
        }
    }

}
