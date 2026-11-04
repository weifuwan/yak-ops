package io.yak.ops.dao.repository.impl;

import static com.google.common.truth.Truth.assertThat;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.repository.CommandDao;
import io.yak.ops.dao.utils.WorkerGroupUtils;

import org.apache.commons.lang3.RandomUtils;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

class CommandDaoImplTest extends BaseDaoTest {

    @Autowired
    private CommandDao commandDao;

    @RepeatedTest(value = 10)
    void fetchCommandByIdSlot() {
        int totalSlot = RandomUtils.nextInt(1, 10);
        int currentSlotIndex = RandomUtils.nextInt(0, totalSlot);
        int fetchSize = RandomUtils.nextInt(10, 100);
        int idStep = RandomUtils.nextInt(1, 5);
        int commandSize = RandomUtils.nextInt(currentSlotIndex, 1000);
        // Generate commandSize commands
        int id = 1;
        for (int j = 0; j < commandSize; j++) {
            Command command = generateCommand(CommandType.START_PROCESS, 0);
            command.setId(id);
            commandDao.insert(command);
            id += idStep;
        }

        List<Command> commands = commandDao.queryCommandByIdSlot(currentSlotIndex, totalSlot, idStep, fetchSize);
        assertThat(commands.size())
                .isEqualTo(commandDao.queryAll()
                        .stream()
                        .filter(command -> (command.getId() / idStep) % totalSlot == currentSlotIndex)
                        .limit(fetchSize)
                        .count());

    }

    private Command generateCommand(CommandType commandType, int processDefinitionCode) {
        Command command = new Command();
        command.setCommandType(commandType);
        command.setWorkflowDefinitionCode(processDefinitionCode);
        command.setExecutorId(4);
        command.setCommandParam("test command param");
        command.setTaskDependType(TaskDependType.TASK_ONLY);
        command.setFailureStrategy(FailureStrategy.CONTINUE);
        command.setWarningType(WarningType.ALL);
        command.setWarningGroupId(1);
        command.setScheduleTime(DateUtils.stringToDate("2019-12-29 12:10:00"));
        command.setWorkflowInstancePriority(Priority.MEDIUM);
        command.setStartTime(DateUtils.stringToDate("2019-12-29 10:10:00"));
        command.setUpdateTime(DateUtils.stringToDate("2019-12-29 10:10:00"));
        command.setWorkerGroup(WorkerGroupUtils.getDefaultWorkerGroup());
        command.setWorkflowInstanceId(0);
        command.setWorkflowDefinitionVersion(0);
        return command;
    }
}
