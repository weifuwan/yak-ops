package io.yak.ops.server.worker.executor;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.TaskEngine;
import io.yak.ops.task.executor.dto.TaskExecutorDTO;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.operations.TaskExecutorReassignMasterRequest;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PhysicalTaskEngineDelegator implements AutoCloseable {

    private final TaskEngine taskEngine;

    private final PhysicalTaskExecutorFactory physicalTaskExecutorFactory;

    private final PhysicalTaskExecutorLifecycleEventReporter physicalTaskExecutorEventReporter;

    private final PhysicalTaskExecutorRepository physicalTaskExecutorRepository;

    public PhysicalTaskEngineDelegator(final PhysicalTaskEngineFactory physicalTaskEngineFactory,
                                       final PhysicalTaskExecutorFactory physicalTaskExecutorFactory,
                                       final PhysicalTaskExecutorRepository physicalTaskExecutorRepository,
                                       final PhysicalTaskExecutorLifecycleEventReporter physicalTaskExecutorEventReporter) {
        this.physicalTaskExecutorFactory = physicalTaskExecutorFactory;
        this.taskEngine = physicalTaskEngineFactory.createTaskEngine();
        this.physicalTaskExecutorRepository = physicalTaskExecutorRepository;
        this.physicalTaskExecutorEventReporter = physicalTaskExecutorEventReporter;
    }

    public void start() {
        taskEngine.start();
        physicalTaskExecutorEventReporter.start();
        log.info("PhysicalTaskEngineDelegator started");
    }

    public void dispatchLogicTask(final TaskExecutionContext taskExecutionContext) {
        final ITaskExecutor taskExecutor = physicalTaskExecutorFactory.createTaskExecutor(taskExecutionContext);
        taskEngine.submitTask(taskExecutor);
    }

    public void killLogicTask(final int taskInstanceId) {
        taskEngine.killTask(taskInstanceId);
    }

    public void pauseLogicTask(final int taskInstanceId) {
        taskEngine.pauseTask(taskInstanceId);
    }

    public List<TaskExecutorDTO> queryTaskExecutors() {
        return taskEngine.queryTaskExecutors();
    }

    public void ackPhysicalTaskExecutorLifecycleEventACK(final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck) {
        physicalTaskExecutorEventReporter.receiveTaskExecutorLifecycleEventACK(taskExecutorLifecycleEventAck);
    }

    public boolean reassignWorkflowInstanceHost(final TaskExecutorReassignMasterRequest taskExecutorReassignMasterRequest) {
        final int taskInstanceId = taskExecutorReassignMasterRequest.getTaskInstanceId();
        final String workflowHost = taskExecutorReassignMasterRequest.getWorkflowHost();
        final Optional<ITaskExecutor> taskExecutorOptional = physicalTaskExecutorRepository.get(taskInstanceId);
        if (taskExecutorOptional.isPresent()) {
            taskExecutorOptional.get().getTaskExecutionContext().setWorkflowInstanceHost(workflowHost);
            physicalTaskExecutorEventReporter.onWorkflowInstanceHostChanged(taskInstanceId);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        taskEngine.close();
    }
}
