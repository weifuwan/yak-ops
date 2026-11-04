package io.yak.ops.server.master.integration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Awaitility.await;

import io.yak.ops.eventbus.AbstractDelayEventBus;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.WorkflowEventBusFireWorkers;
import io.yak.ops.server.master.engine.executor.LogicTaskExecutorContainerProvider;
import io.yak.ops.server.master.engine.executor.LogicTaskExecutorLifecycleEventReporter;
import io.yak.ops.server.master.engine.executor.LogicTaskExecutorRepository;
import io.yak.ops.server.master.engine.system.SystemEventBus;
import io.yak.ops.server.master.engine.task.dispatcher.WorkerGroupDispatcherCoordinator;
import io.yak.ops.task.executor.container.SharedThreadTaskExecutorContainer;
import io.yak.ops.task.executor.container.TaskExecutorAssignmentTable;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MasterContainer {

    @Autowired
    private SystemEventBus systemEventBus;

    @Autowired
    private IWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowEventBusFireWorkers workflowEventBusFireWorkers;

    @Autowired
    private LogicTaskExecutorRepository logicTaskExecutorRepository;

    @Autowired
    private LogicTaskExecutorContainerProvider logicTaskExecutorContainerProvider;

    @Autowired
    private LogicTaskExecutorLifecycleEventReporter logicTaskExecutorLifecycleEventReporter;

    @Autowired
    private WorkerGroupDispatcherCoordinator workerGroupDispatcherCoordinator;

    public void assertAllResourceReleased() {
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(this::doAssertAllResourceReleased);
    }

    private void doAssertAllResourceReleased() {
        assertWorkflowReleased();
        assertWorkflowEventBusReleased();

        assertSystemEventBusReleased();

        assertLogicTaskEngineReleased();

        assertWorkerGroupDispatcherReleased();
    }

    private void assertWorkflowReleased() {
        assertThat(workflowRepository.getAll()).isEmpty();
        assertThat(workflowEventBusFireWorkers.getWorkers())
                .allMatch(worker -> worker.getRegisteredWorkflowExecuteRunnableSize() == 0);
    }

    private void assertWorkflowEventBusReleased() {
        assertThat(workflowEventBusFireWorkers.getWorkers())
                .allMatch(worker -> worker.getRegisteredWorkflowExecuteRunnableSize() == 0);
    }

    private void assertSystemEventBusReleased() {
        assertThat(systemEventBus).matches(AbstractDelayEventBus::isEmpty);
    }

    private void assertLogicTaskEngineReleased() {
        assertThat(logicTaskExecutorRepository.getAll()).isEmpty();

        final SharedThreadTaskExecutorContainer executorContainer =
                logicTaskExecutorContainerProvider.getExecutorContainer();
        assertThat(executorContainer.getTaskExecutorAssignmentTable()).matches(TaskExecutorAssignmentTable::isEmpty);

        assertThat(executorContainer.getTaskExecutorWorkers().getWorkers())
                .allMatch(taskExecutorWorker -> taskExecutorWorker.getRegisteredTaskExecutorSize() == 0)
                .allMatch(taskExecutorWorker -> taskExecutorWorker.getFiredTaskExecutorSize() == 0);

        assertThat(logicTaskExecutorLifecycleEventReporter.getEventChannels()).isEmpty();
    }

    private void assertWorkerGroupDispatcherReleased() {
        assertThat(workerGroupDispatcherCoordinator.workerGroupDispatchers().values())
                .allMatch(dispatcher -> dispatcher.dispatchEventCount() == 0)
                .allMatch(dispatcher -> dispatcher.waitingDispatchTaskCount() == 0);
    }

}
