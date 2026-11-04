package io.yak.ops.server.master.engine.command.handler;

import static com.google.common.base.Preconditions.checkArgument;
import static io.yak.ops.common.utils.JSONUtils.parseObject;

import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionLogDao;
import io.yak.ops.extract.master.command.ICommandParam;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.command.ICommandHandler;
import io.yak.ops.server.master.engine.graph.IWorkflowGraph;
import io.yak.ops.server.master.engine.graph.WorkflowGraphFactory;
import io.yak.ops.server.master.engine.workflow.execution.WorkflowExecution;
import io.yak.ops.server.master.engine.workflow.execution.WorkflowExecutionBuilder;
import io.yak.ops.server.master.engine.workflow.listener.IWorkflowLifecycleListener;
import io.yak.ops.server.master.runner.WorkflowExecuteContext;
import io.yak.ops.server.master.runner.WorkflowExecuteContext.WorkflowExecuteContextBuilder;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public abstract class AbstractCommandHandler implements ICommandHandler {

    @Autowired
    protected WorkflowDefinitionLogDao workflowDefinitionLogDao;

    @Autowired
    protected WorkflowGraphFactory workflowGraphFactory;

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    protected TaskInstanceDao taskInstanceDao;

    @Autowired
    protected List<IWorkflowLifecycleListener> workflowLifecycleListeners;

    @Autowired
    protected ProjectDao projectDao;

    @Override
    public WorkflowExecution handleCommand(final Command command) {
        final WorkflowExecuteContextBuilder workflowExecuteContextBuilder = WorkflowExecuteContext.builder()
                .withCommand(command);

        assembleWorkflowDefinition(workflowExecuteContextBuilder);
        assembleProject(workflowExecuteContextBuilder);
        assembleWorkflowGraph(workflowExecuteContextBuilder);
        assembleWorkflowInstance(workflowExecuteContextBuilder);
        assembleWorkflowInstanceLifecycleListeners(workflowExecuteContextBuilder);
        assembleWorkflowEventBus(workflowExecuteContextBuilder);
        assembleWorkflowExecutionGraph(workflowExecuteContextBuilder);

        final WorkflowExecutionBuilder workflowExecutionBuilder = WorkflowExecutionBuilder
                .builder()
                .workflowExecuteContextBuilder(workflowExecuteContextBuilder)
                .applicationContext(applicationContext)
                .build();
        return new WorkflowExecution(workflowExecutionBuilder);
    }

    protected void assembleWorkflowEventBus(
                                            final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        workflowExecuteContextBuilder.setWorkflowEventBus(new WorkflowEventBus());
    }

    protected void assembleWorkflowInstanceLifecycleListeners(
                                                              final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        workflowExecuteContextBuilder.setWorkflowInstanceLifecycleListeners(workflowLifecycleListeners);
    }

    protected void assembleWorkflowDefinition(
                                              final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final Command command = workflowExecuteContextBuilder.getCommand();
        final long workflowDefinitionCode = command.getWorkflowDefinitionCode();
        final int workflowDefinitionVersion = command.getWorkflowDefinitionVersion();

        final WorkflowDefinition workflowDefinition = workflowDefinitionLogDao.queryByDefinitionCodeAndVersion(
                workflowDefinitionCode,
                workflowDefinitionVersion);
        checkArgument(workflowDefinition != null,
                "Cannot find the WorkflowDefinition: [" + workflowDefinitionCode + ":" + workflowDefinitionVersion
                        + "]");
        workflowExecuteContextBuilder.setWorkflowDefinition(workflowDefinition);

    }

    protected void assembleWorkflowGraph(
                                         final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final WorkflowDefinition workflowDefinition = workflowExecuteContextBuilder.getWorkflowDefinition();
        workflowExecuteContextBuilder.setWorkflowGraph(workflowGraphFactory.createWorkflowGraph(workflowDefinition));
    }

    protected abstract void assembleWorkflowInstance(
                                                     final WorkflowExecuteContextBuilder workflowExecuteContextBuilder);

    protected abstract void assembleWorkflowExecutionGraph(
                                                           final WorkflowExecuteContextBuilder workflowExecuteContextBuilder);

    protected List<String> parseStartNodesFromWorkflowInstance(
                                                               final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final WorkflowInstance workflowInstance = workflowExecuteContextBuilder.getWorkflowInstance();
        final ICommandParam commandParam = parseObject(workflowInstance.getCommandParam(), ICommandParam.class);
        checkArgument(commandParam != null, "Invalid command param : " + workflowInstance.getCommandParam());
        List<Long> startCodes = commandParam.getStartNodes();
        if (CollectionUtils.isEmpty(startCodes)) {
            return Collections.emptyList();
        }
        final IWorkflowGraph workflowGraph = workflowExecuteContextBuilder.getWorkflowGraph();
        return startCodes
                .stream()
                .map(workflowGraph::getTaskNodeByCode)
                .map(TaskDefinition::getName)
                .collect(Collectors.toList());

    }

    protected List<TaskInstance> getValidTaskInstance(final WorkflowInstance workflowInstance) {
        return taskInstanceDao.queryValidTaskListByWorkflowInstanceId(
                workflowInstance.getId());
    }

    protected void assembleProject(
                                   final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final WorkflowDefinition workflowDefinition = workflowExecuteContextBuilder.getWorkflowDefinition();
        final Project project = projectDao.queryByCode(workflowDefinition.getProjectCode());
        checkArgument(project != null, "Cannot find the project code: " + workflowDefinition.getProjectCode());
        workflowExecuteContextBuilder.setProject(project);
    }

}
