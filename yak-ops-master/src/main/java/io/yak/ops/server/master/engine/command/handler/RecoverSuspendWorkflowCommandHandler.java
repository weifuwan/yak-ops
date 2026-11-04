package io.yak.ops.server.master.engine.command.handler;

import io.yak.ops.common.enums.CommandType;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RecoverSuspendWorkflowCommandHandler extends RecoverFailureTaskCommandHandler {

    @Override
    public CommandType commandType() {
        return CommandType.RECOVER_SUSPENDED_PROCESS;
    }
}
