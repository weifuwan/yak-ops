package io.yak.ops.service.command;

import static io.yak.ops.common.constants.CommandKeyConstants.CMD_PARAM_RECOVER_WORKFLOW_ID_STRING;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.ErrorCommand;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.mapper.CommandMapper;
import io.yak.ops.dao.mapper.ErrorCommandMapper;
import io.yak.ops.dao.mapper.ScheduleMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micrometer.core.annotation.Counted;
import lombok.extern.slf4j.Slf4j;

/**
 * Command Service implementation
 */
@Component
@Slf4j
public class CommandServiceImpl implements CommandService {

    @Autowired
    private ErrorCommandMapper errorCommandMapper;

    @Autowired
    private CommandMapper commandMapper;

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Override
    public void moveToErrorCommand(Command command, String message) {
        final ErrorCommand errorCommand = new ErrorCommand(command, message);
        errorCommandMapper.insert(errorCommand);
        commandMapper.deleteById(command.getId());
    }

    @Override
    @Counted("ds.workflow.create.command.count")
    public int createCommand(Command command) {
        int result = 0;
        if (command == null) {
            return result;
        }
        // add command timezone
        Schedule schedule = scheduleMapper.queryByWorkflowDefinitionCode(command.getWorkflowDefinitionCode());
        if (schedule != null) {
            Map<String, String> commandParams =
                    StringUtils.isNotBlank(command.getCommandParam()) ? JSONUtils.toMap(command.getCommandParam())
                            : new HashMap<>();
            commandParams.put(Constants.SCHEDULE_TIMEZONE, schedule.getTimezoneId());
            command.setCommandParam(JSONUtils.toJsonString(commandParams));
        }
        command.setId(null);
        result = commandMapper.insert(command);
        return result;
    }

    @Override
    public boolean verifyIsNeedCreateCommand(Command command) {
        boolean isNeedCreate = true;
        EnumMap<CommandType, Integer> cmdTypeMap = new EnumMap<>(CommandType.class);
        cmdTypeMap.put(CommandType.REPEAT_RUNNING, 1);
        cmdTypeMap.put(CommandType.RECOVER_SUSPENDED_PROCESS, 1);
        cmdTypeMap.put(CommandType.START_FAILURE_TASK_PROCESS, 1);
        CommandType commandType = command.getCommandType();

        if (!cmdTypeMap.containsKey(commandType)) {
            return true;
        }

        ObjectNode cmdParamObj = JSONUtils.parseObject(command.getCommandParam());
        int processInstanceId = cmdParamObj.path(CMD_PARAM_RECOVER_WORKFLOW_ID_STRING).asInt();

        List<Command> commands = commandMapper.selectList(null);
        // for all commands
        for (Command tmpCommand : commands) {
            if (!cmdTypeMap.containsKey(tmpCommand.getCommandType())) {
                continue;
            }
            ObjectNode tempObj = JSONUtils.parseObject(tmpCommand.getCommandParam());
            if (tempObj != null
                    && processInstanceId == tempObj.path(CMD_PARAM_RECOVER_WORKFLOW_ID_STRING).asInt()) {
                isNeedCreate = false;
                break;
            }
        }
        return isNeedCreate;
    }

}
