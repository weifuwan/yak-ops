package io.yak.ops.server.master.runner;

import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.Environment;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.IEnvironmentDao;
import io.yak.ops.dao.utils.EnvironmentUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.resource.AbstractResourceParameters;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;
import io.yak.ops.plugin.task.api.utils.MapUtils;
import io.yak.ops.plugin.task.api.utils.VarPoolUtils;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.execution.TaskExecutionContextBuilder;
import io.yak.ops.server.master.engine.task.execution.TaskExecutionContextCreateRequest;
import io.yak.ops.service.expand.CuringParamsService;
import io.yak.ops.service.process.ProcessService;

import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskExecutionContextFactory {

    @Autowired
    private ProcessService processService;

    @Autowired
    private CuringParamsService curingParamsService;

    @Autowired
    private MasterConfig masterConfig;

    @Autowired
    private IEnvironmentDao environmentDao;

    public TaskExecutionContext createTaskExecutionContext(TaskExecutionContextCreateRequest request) {
        final TaskInstance taskInstance = request.getTaskInstance();
        final WorkflowInstance workflowInstance = request.getWorkflowInstance();
        final WorkflowDefinition workflowDefinition = request.getWorkflowDefinition();
        final Project project = request.getProject();

        final List<Property> varPools =
                generateTaskInstanceVarPool(workflowInstance, request.getTaskDefinition(),
                        request.getWorkflowExecutionGraph());
        taskInstance.setVarPool(VarPoolUtils.serializeVarPool(varPools));

        return TaskExecutionContextBuilder.get()
                .buildWorkflowInstanceHost(masterConfig.getMasterAddress())
                .buildTaskInstanceRelatedInfo(taskInstance)
                .buildEnvironmentConfig(getEnvironmentConfigFromDB(taskInstance).orElse(null))
                .buildTaskDefinitionRelatedInfo(request.getTaskDefinition())
                .buildProcessInstanceRelatedInfo(request.getWorkflowInstance())
                .buildResourceParameters(getResourceParameters(taskInstance))
                // todo: use TaskRuntimeParameters to replace Map<String, Property> in TaskExecutionContext
                .buildPrepareParams(getPrepareParams(taskInstance, workflowInstance, workflowDefinition, project))
                .create();
    }

    private ResourceParametersHelper getResourceParameters(final TaskInstance taskInstance) {
        final ResourceParametersHelper resourceParameters = TaskPluginManager.getTaskChannel(taskInstance.getTaskType())
                .parseParameters(taskInstance.getTaskParams())
                .getResources();
        if (resourceParameters != null) {
            // todo: add DataSourceParametersAssembler to assemble DataSourceParameters
            resourceParameters.getResourceMap().forEach((type, map) -> {
                switch (type) {
                    case DATASOURCE:
                        assembleDataSourceParameters(map);
                        break;
                    default:
                        break;
                }
            });
        }
        return resourceParameters;
    }

    private void assembleDataSourceParameters(Map<Integer, AbstractResourceParameters> map) {
        if (MapUtils.isEmpty(map)) {
            return;
        }

        map.forEach((code, parameters) -> {
            DataSource datasource = processService.findDataSourceById(code);
            if (Objects.isNull(datasource)) {
                return;
            }
            DataSourceParameters dataSourceParameters = new DataSourceParameters();
            dataSourceParameters.setType(datasource.getType());
            dataSourceParameters.setConnectionParams(datasource.getConnectionParams());
            map.put(code, dataSourceParameters);
        });
    }

    private Map<String, Property> getPrepareParams(final TaskInstance taskInstance,
                                                   final WorkflowInstance workflowInstance,
                                                   final WorkflowDefinition workflowDefinition,
                                                   final Project project) {
        final AbstractParameters baseParam = TaskPluginManager.parseTaskParameters(
                taskInstance.getTaskType(),
                taskInstance.getTaskParams());

        return curingParamsService.paramParsingPreparation(
                taskInstance,
                baseParam,
                workflowInstance,
                project.getName(),
                workflowDefinition.getName());
    }

    private Optional<String> getEnvironmentConfigFromDB(final TaskInstance taskInstance) {
        if (EnvironmentUtils.isEnvironmentCodeEmpty(taskInstance.getEnvironmentCode())) {
            return Optional.empty();
        }
        final Optional<Environment> environmentOptional =
                environmentDao.queryByEnvironmentCode(taskInstance.getEnvironmentCode());
        if (!environmentOptional.isPresent()) {
            throw new IllegalArgumentException("Cannot find the environment: " + taskInstance.getEnvironmentCode());
        }
        return Optional.ofNullable(environmentOptional.get().getConfig());
    }

    // The predecessors of the task instance will be used to generate the var pool.
    // In execute-task(TASK_ONLY) scenario, the predecessor might be outside current execution sub-graph.
    // For this case, fallback to workflow varPool to keep compatibility with historical behavior.
    private List<Property> generateTaskInstanceVarPool(WorkflowInstance workflowInstance,
                                                       TaskDefinition taskDefinition,
                                                       IWorkflowExecutionGraph workflowExecutionGraph) {
        final boolean isStartNode = workflowExecutionGraph.getStartNodes()
                .stream()
                .anyMatch(node -> node.getTaskDefinition().getCode() == taskDefinition.getCode());
        if (isStartNode) {
            return VarPoolUtils.deserializeVarPool(workflowInstance.getVarPool());
        }

        List<String> varPoolsFromPredecessors = workflowExecutionGraph.getPredecessors(taskDefinition.getName())
                .stream()
                .filter(ITaskExecution::isTaskInstanceInitialized)
                .map(ITaskExecution::getTaskInstance)
                .sorted(Comparator.comparing(TaskInstance::getEndTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(TaskInstance::getVarPool)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        return VarPoolUtils.mergeVarPoolJsonString(varPoolsFromPredecessors);
    }

}
