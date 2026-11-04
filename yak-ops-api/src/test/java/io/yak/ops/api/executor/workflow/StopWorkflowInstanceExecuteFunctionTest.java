package io.yak.ops.api.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StopWorkflowInstanceExecuteFunctionTest {

    @Mock
    private WorkflowInstanceDao workflowInstanceDao;

    @InjectMocks
    private StopWorkflowInstanceExecutorDelegate stopWorkflowInstanceExecutorDelegate;

    @BeforeAll
    public static void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
    }

    @AfterAll
    public static void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowExecutionStatus.class, names = {
            "RUNNING_EXECUTION",
            "READY_PAUSE",
            "READY_STOP",
            "SERIAL_WAIT"})
    void exceptionIfWorkflowInstanceCannotStop_canStop(WorkflowExecutionStatus workflowExecutionStatus) {
        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setName("Workflow-1");
        workflowInstance.setState(workflowExecutionStatus);
        assertDoesNotThrow(
                () -> stopWorkflowInstanceExecutorDelegate.exceptionIfWorkflowInstanceCannotStop(workflowInstance));
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowExecutionStatus.class, names = {
            "RUNNING_EXECUTION",
            "READY_PAUSE",
            "READY_STOP",
            "SERIAL_WAIT"}, mode = EnumSource.Mode.EXCLUDE)
    void exceptionIfWorkflowInstanceCannotStop_canNotStop(WorkflowExecutionStatus workflowExecutionStatus) {
        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setName("Workflow-1");
        workflowInstance.setState(workflowExecutionStatus);
        ServiceException serviceException = assertThrows(ServiceException.class,
                () -> stopWorkflowInstanceExecutorDelegate.exceptionIfWorkflowInstanceCannotStop(workflowInstance));
        Assertions.assertEquals(
                "Internal Server Error: The workflow instance: Workflow-1 status is " + workflowExecutionStatus
                        + ", can not stop",
                serviceException.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowExecutionStatus.class, names = {
            "SERIAL_WAIT"})
    void ifWorkflowInstanceCanDirectStopInDB_canDirectStopInDB(WorkflowExecutionStatus workflowExecutionStatus) {
        Assertions.assertTrue(workflowExecutionStatus.isCanDirectStopInDB());
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowExecutionStatus.class, names = {
            "SERIAL_WAIT"}, mode = EnumSource.Mode.EXCLUDE)
    void ifWorkflowInstanceCanDirectStopInDB_canNotDirectStopInDB(WorkflowExecutionStatus workflowExecutionStatus) {
        Assertions.assertFalse(workflowExecutionStatus.isCanDirectStopInDB());
    }
}
