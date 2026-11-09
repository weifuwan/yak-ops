package io.yak.ops.server.master.engine.workflow.serial;

import io.yak.ops.dao.repository.SerialCommandDao;
import io.yak.ops.dao.repository.WorkflowDefinitionLogDao;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class WorkflowSerialCoordinatorTest {

    private WorkflowSerialCoordinator createCoordinator() {
        WorkflowSerialCoordinator coordinator = new WorkflowSerialCoordinator();
        ReflectionTestUtils.setField(coordinator, "serialCommandDao", Mockito.mock(SerialCommandDao.class));
        ReflectionTestUtils.setField(
                coordinator, "workflowDefinitionLogDao", Mockito.mock(WorkflowDefinitionLogDao.class));
        ReflectionTestUtils.setField(
                coordinator, "serialCommandWaitHandler", Mockito.mock(SerialCommandWaitHandler.class));
        ReflectionTestUtils.setField(
                coordinator, "serialCommandDiscardHandler", Mockito.mock(SerialCommandDiscardHandler.class));
        ReflectionTestUtils.setField(
                coordinator, "serialCommandPriorityHandler", Mockito.mock(SerialCommandPriorityHandler.class));
        return coordinator;
    }

    @Test
    void startAfterCloseShouldNotThrow() {
        WorkflowSerialCoordinator coordinator = createCoordinator();
        coordinator.start();
        coordinator.close();

        Assertions.assertDoesNotThrow(coordinator::start);
        coordinator.close();
    }

    @Test
    void closeShouldBeIdempotent() {
        WorkflowSerialCoordinator coordinator = createCoordinator();
        coordinator.start();
        coordinator.close();

        Assertions.assertDoesNotThrow(coordinator::close);
    }

    @Test
    void startWhenAlreadyStartedShouldThrow() {
        WorkflowSerialCoordinator coordinator = createCoordinator();
        coordinator.start();

        Assertions.assertThrows(IllegalStateException.class, coordinator::start);
        coordinator.close();
    }
}
