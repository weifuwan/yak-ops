package io.yak.ops.server.master.engine.command.handler;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.extract.master.command.WorkflowFailoverCommandParam;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.engine.graph.IWorkflowGraph;
import io.yak.ops.server.master.engine.graph.WorkflowExecutionGraph;
import io.yak.ops.server.master.engine.graph.WorkflowGraphTopologyLogicalVisitor;
import io.yak.ops.server.master.engine.task.execution.TaskExecution;
import io.yak.ops.server.master.engine.task.execution.TaskExecutionBuilder;
import io.yak.ops.server.master.runner.WorkflowExecuteContext.WorkflowExecuteContextBuilder;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * This handler used to handle {@link CommandType#RECOVER_TOLERANCE_FAULT_PROCESS}.
 * <p> Will do failover of the workflow instance and recover it to the origin state.
 */
@Component
public class WorkflowFailoverCommandHandler extends AbstractCommandHandler {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MasterConfig masterConfig;

    /**
     * Generate the recover workflow instance.
     * <p> Will use the origin workflow instance, but will update the following fields. Need to note we cannot not
     * update the command params here, since this will make the origin command params lost.
     * <ul>
     *     <li>state</li>
     *     <li>command type</li>
     *     <li>start time</li>
     *     <li>restart time</li>
     *     <li>end time</li>
     *     <li>run times</li>
     * </ul>
     */
    @Override
    protected void assembleWorkflowInstance(
                                            final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final Command command = workflowExecuteContextBuilder.getCommand();
        final int workflowInstanceId = command.getWorkflowInstanceId();
        final WorkflowInstance workflowInstance = workflowInstanceDao.queryOptionalById(workflowInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find WorkflowInstance:" + workflowInstanceId));
        final WorkflowFailoverCommandParam workflowFailoverCommandParam = JSONUtils.parseObject(
                command.getCommandParam(),
                WorkflowFailoverCommandParam.class);
        if (workflowFailoverCommandParam == null) {
            throw new IllegalArgumentException(
                    "The WorkflowFailoverCommandParam: " + command.getCommandParam() + " is invalid");
        }
        workflowInstance.setRestartTime(new Date());
        workflowInstance.setState(workflowFailoverCommandParam.getWorkflowExecutionStatus());
        workflowInstance.setHost(masterConfig.getMasterAddress());
        workflowInstanceDao.updateById(workflowInstance);

        workflowExecuteContextBuilder.setWorkflowInstance(workflowInstance);
    }

    /**
     * Generate the workflow execution graph.
     * <p> Will rebuild the WorkflowExecutionGraph from the exist task instance.
     */
    @Override
    protected void assembleWorkflowExecutionGraph(final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final Map<String, TaskInstance> taskInstanceMap =
                getValidTaskInstance(workflowExecuteContextBuilder.getWorkflowInstance())
                        .stream()
                        .collect(Collectors.toMap(TaskInstance::getName, Function.identity()));

        final IWorkflowGraph workflowGraph = workflowExecuteContextBuilder.getWorkflowGraph();
        final WorkflowExecutionGraph workflowExecutionGraph = new WorkflowExecutionGraph();

        final BiConsumer<String, Set<String>> taskExecutionCreator = (task, successors) -> {
            final TaskExecutionBuilder taskExecutionBuilder =
                    TaskExecutionBuilder
                            .builder()
                            .workflowExecutionGraph(workflowExecutionGraph)
                            .workflowDefinition(workflowExecuteContextBuilder.getWorkflowDefinition())
                            .project(workflowExecuteContextBuilder.getProject())
                            .workflowInstance(workflowExecuteContextBuilder.getWorkflowInstance())
                            .taskDefinition(workflowGraph.getTaskNodeByName(task))
                            .taskInstance(taskInstanceMap.get(task))
                            .workflowEventBus(workflowExecuteContextBuilder.getWorkflowEventBus())
                            .applicationContext(applicationContext)
                            .build();
            workflowExecutionGraph.addNode(new TaskExecution(taskExecutionBuilder));
            workflowExecutionGraph.addEdge(task, successors);
        };

        final WorkflowGraphTopologyLogicalVisitor workflowGraphTopologyLogicalVisitor =
                WorkflowGraphTopologyLogicalVisitor.builder()
                        .taskDependType(workflowExecuteContextBuilder.getWorkflowInstance().getTaskDependType())
                        .onWorkflowGraph(workflowGraph)
                        .fromTask(parseStartNodesFromWorkflowInstance(workflowExecuteContextBuilder))
                        .doVisitFunction(taskExecutionCreator)
                        .build();
        workflowGraphTopologyLogicalVisitor.visit();
        workflowExecutionGraph.removeUnReachableEdge();

        workflowExecuteContextBuilder.setWorkflowExecutionGraph(workflowExecutionGraph);
    }

    @Override
    public CommandType commandType() {
        return CommandType.RECOVER_TOLERANCE_FAULT_PROCESS;
    }

}
