package io.yak.ops.server.master.engine.command.handler;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.scheduler.api.SchedulerApi;

import org.springframework.stereotype.Component;

/**
 * Used to handle the {@link CommandType#SCHEDULER} which will start the workflow definition by {@link SchedulerApi}.
 */
@Component
public class ScheduleWorkflowCommandHandler extends RunWorkflowCommandHandler {

    @Override
    public CommandType commandType() {
        return CommandType.SCHEDULER;
    }
}
