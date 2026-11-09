package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;

class TaskLifecyclePersistenceRecoveryTest {

    @Test
    void shouldKeepSharedTaskStateUnchangedUntilPersistenceSucceeds() {
        TaskInstanceDao taskInstanceDao = Mockito.mock(TaskInstanceDao.class);
        TaskSuccessStateAction action = new TaskSuccessStateAction();
        action.taskInstanceDao = taskInstanceDao;

        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setName("retryable-task");
        taskInstance.setState(TaskExecutionStatus.SUBMITTED_SUCCESS);
        Date endTime = new Date();

        Mockito.when(taskInstanceDao.updateById(Mockito.any(TaskInstance.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"))
                .thenReturn(true);

        Assertions.assertThrows(
                DataAccessResourceFailureException.class,
                () -> action.persistTaskInstanceUpdate(taskInstance, instance -> {
                    instance.setState(TaskExecutionStatus.SUCCESS);
                    instance.setEndTime(endTime);
                }));

        Assertions.assertEquals(TaskExecutionStatus.SUBMITTED_SUCCESS, taskInstance.getState());
        Assertions.assertNull(taskInstance.getEndTime());

        action.persistTaskInstanceUpdate(taskInstance, instance -> {
            instance.setState(TaskExecutionStatus.SUCCESS);
            instance.setEndTime(endTime);
        });

        Assertions.assertEquals(TaskExecutionStatus.SUCCESS, taskInstance.getState());
        Assertions.assertEquals(endTime, taskInstance.getEndTime());
        Mockito.verify(taskInstanceDao, Mockito.times(2)).updateById(Mockito.argThat(
                candidate -> candidate != taskInstance));
    }
}
