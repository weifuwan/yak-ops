package io.yak.ops.server.master.engine.task.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.AbstractLifecycleEvent;
import io.yak.ops.server.master.engine.ITaskGroupCoordinator;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskStartLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskForceSuccessStateActionTest {

    private TaskForceSuccessStateAction taskForceSuccessStateAction;

    @Mock
    private ITaskGroupCoordinator taskGroupCoordinator;

    @Mock
    private TaskInstanceDao taskInstanceDao;

    @Mock
    private ITaskExecution taskExecution;

    @Mock
    private IWorkflowExecution workflowExecution;

    @Mock
    private WorkflowEventBus workflowEventBus;

    private TaskInstance taskInstance;

    @BeforeEach
    void setUp() {
        taskForceSuccessStateAction = new TaskForceSuccessStateAction();
        taskForceSuccessStateAction.taskGroupCoordinator = taskGroupCoordinator;
        taskForceSuccessStateAction.taskInstanceDao = taskInstanceDao;

        taskInstance = new TaskInstance();
        taskInstance.setState(TaskExecutionStatus.FORCED_SUCCESS);

        when(taskExecution.getTaskInstance()).thenReturn(taskInstance);
        when(taskExecution.getWorkflowEventBus()).thenReturn(workflowEventBus);
        when(workflowExecution.getWorkflowInstance()).thenReturn(new WorkflowInstance());
    }

    @Test
    void shouldPreserveForcedSuccessWhileContinuingWorkflow() {
        taskForceSuccessStateAction.onStartEvent(
                workflowExecution,
                taskExecution,
                TaskStartLifecycleEvent.of(taskExecution));

        assertThat(taskInstance.getState()).isEqualTo(TaskExecutionStatus.FORCED_SUCCESS);
        verify(workflowEventBus).publish(any(AbstractLifecycleEvent.class));
    }
}
