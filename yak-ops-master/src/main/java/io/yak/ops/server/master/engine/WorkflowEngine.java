package io.yak.ops.server.master.engine;

import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.dto.WorkflowExecutorDTO;
import io.yak.ops.server.master.engine.command.CommandEngine;
import io.yak.ops.server.master.engine.executor.LogicTaskEngineDelegator;
import io.yak.ops.server.master.engine.task.dispatcher.WorkerGroupDispatcherCoordinator;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowEngine implements AutoCloseable {

    @Autowired
    private WorkflowEventBusCoordinator workflowEventBusCoordinator;

    @Autowired
    private CommandEngine commandEngine;

    @Autowired
    private WorkerGroupDispatcherCoordinator workerGroupDispatcherCoordinator;

    @Autowired
    private LogicTaskEngineDelegator logicTaskEngineDelegator;

    @Autowired
    private IWorkflowRepository workflowRepository;

    public void start() {

        workflowEventBusCoordinator.start();

        commandEngine.start();

        workerGroupDispatcherCoordinator.start();

        logicTaskEngineDelegator.start();

        log.info("WorkflowEngine started");
    }

    public List<WorkflowExecutorDTO> queryWorkflowExecutors() {
        return workflowRepository.getAll()
                .stream()
                .map(runnable -> {
                    WorkflowInstance wi = runnable.getWorkflowInstance();
                    return WorkflowExecutorDTO.builder()
                            .id(wi.getId())
                            .name(wi.getName())
                            .projectCode(wi.getProjectCode())
                            .workflowDefinitionCode(wi.getWorkflowDefinitionCode())
                            .state(wi.getState())
                            .startTime(wi.getStartTime())
                            .runTimes(wi.getRunTimes())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public void close() throws Exception {
        try (
                final CommandEngine ignore1 = commandEngine;
                final WorkflowEventBusCoordinator ignore2 = workflowEventBusCoordinator;
                final WorkerGroupDispatcherCoordinator ignore3 = workerGroupDispatcherCoordinator;
                final LogicTaskEngineDelegator ignore5 = logicTaskEngineDelegator) {
            // closed the resource
        }
    }
}
