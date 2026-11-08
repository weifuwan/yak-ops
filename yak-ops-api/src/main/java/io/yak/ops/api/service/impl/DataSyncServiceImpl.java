package io.yak.ops.api.service.impl;

import io.yak.ops.api.dto.datasync.DataSyncPublishRequest;
import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.DataSyncService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.WorkflowDefinitionService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.DataSyncDefinitionVO;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.api.vo.DataSyncTaskVersionVO;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.TimeoutFlag;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.common.enums.WorkflowExecutionTypeEnum;
import io.yak.ops.common.utils.CodeGenerateUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.model.DataSyncTaskPageRow;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;
import io.yak.ops.plugin.task.datasync.DataSyncParameters;
import io.yak.ops.plugin.task.datasync.DataSyncPipelineBuilder;
import io.yak.ops.spi.enums.DbType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncServiceImpl extends BaseServiceImpl implements DataSyncService {

    private static final String TASK_TYPE = "DATA_SYNC";
    private final ProjectService projectService;
    private final DataSourceDao dataSourceDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final TaskInstanceDao taskInstanceDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowInstanceDao workflowInstanceDao;
    private final WorkflowTaskRelationDao workflowTaskRelationDao;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final DataSyncPipelineBuilder dataSyncPipelineBuilder = new DataSyncPipelineBuilder();

    @Override
    public PageInfo<DataSyncTaskVO> listTasks(User user, long projectCode,
                                              int pageNo, int pageSize, String keyword, ReleaseState status) {
        authorizeRead(user, projectCode);
        String search = keyword == null ? null : keyword.trim();
        Long taskCode = null;
        if (search != null && !search.isEmpty()) {
            try {
                taskCode = Long.valueOf(search);
            } catch (NumberFormatException ignored) {
                // Search by name when the input is not a task code.
            }
        }
        Integer releaseState = status == null ? null : status.getCode();
        IPage<DataSyncTaskPageRow> page = taskDefinitionDao.queryDataSyncPage(
                new Page<>(pageNo, pageSize), projectCode, TASK_TYPE, search, taskCode, releaseState);
        List<DataSyncTaskVO> rows = page.getRecords().stream()
                .map(row -> toTaskVO(row, ReleaseState.getEnum(row.getWorkflowReleaseState()),
                        row.getWorkflowCode(), false))
                .collect(Collectors.toList());
        PageInfo<DataSyncTaskVO> result = PageInfo.of(pageNo, pageSize);
        result.setTotalList(rows);
        result.setTotal((int) page.getTotal());
        return result;
    }

    @Override
    public DataSyncTaskVO getTask(User user, long projectCode, long taskCode) {
        authorizeRead(user, projectCode);
        TaskDefinition definition = requireTask(projectCode, taskCode, true);
        return toTaskVO(definition, requireWorkflow(projectCode, taskCode, true));
    }

    @Override
    public String preview(User user, long projectCode, DataSyncTaskRequest request) {
        authorizeWrite(user, projectCode);
        DataSource[] dataSources = validate(request.getName(), request.getParameters(), projectCode);
        return dataSyncPipelineBuilder.preview(
                request.getParameters().path("definition"),
                toDataSourceParameters(dataSources[0]),
                toDataSourceParameters(dataSources[1]));
    }

    @Override
    @Transactional
    public DataSyncTaskVO createTask(User user, long projectCode, DataSyncTaskRequest request) {
        authorizeWrite(user, projectCode);
        validate(request.getName(), request.getParameters(), projectCode);
        long taskCode = CodeGenerateUtils.genCode();
        TaskDefinitionLog task = newTask(taskCode, user, projectCode, request);
        WorkflowTaskRelationLog relation = newRelation(taskCode, Constants.VERSION_FIRST, task.getName());
        WorkflowDefinition workflow = workflowDefinitionService.createWorkflowDefinition(
                user, projectCode, "data_sync_" + taskCode, request.getRemark() == null ? "" : request.getRemark(),
                "[]", "[]", 0, JSONUtils.toJsonString(Collections.singletonList(relation)),
                JSONUtils.toJsonString(Collections.singletonList(task)), "{}",
                WorkflowExecutionTypeEnum.SERIAL_DISCARD);
        return toTaskVO(requireTask(projectCode, taskCode), workflow);
    }

    @Override
    @Transactional
    public DataSyncTaskVO updateTask(User user, long projectCode, long taskCode, DataSyncTaskRequest request) {
        authorizeWrite(user, projectCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        TaskDefinition original = requireTask(projectCode, taskCode);
        if (workflow.getReleaseState() == ReleaseState.ONLINE) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_ALLOWED_EDIT, workflow.getCode());
        }
        if (request.getExpectedRevision() == null || request.getExpectedRevision() != original.getVersion()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "expectedRevision");
        }
        JsonNode parameters = request.getParameters() == null
                ? JSONUtils.parseObject(original.getTaskParams())
                : request.getParameters();
        validate(request.getName(), parameters, projectCode);
        DataSyncTaskRequest effective = new DataSyncTaskRequest();
        effective.setName(request.getName());
        effective.setRemark(request.getRemark());
        effective.setParameters(parameters);
        TaskDefinitionLog updated = newTask(taskCode, user, projectCode, effective);
        updated.setVersion(original.getVersion());
        if (Objects.equals(original.getName(), updated.getName())
                && Objects.equals(original.getDescription(), updated.getDescription())
                && Objects.equals(JSONUtils.parseObject(original.getTaskParams()), parameters)) {
            return getTask(user, projectCode, taskCode);
        }
        if (!taskDefinitionDao.compareAndSetDataSyncRevision(projectCode, taskCode, original.getVersion())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "expectedRevision");
        }
        WorkflowTaskRelationLog relation = newRelation(taskCode, original.getVersion(), updated.getName());
        workflowDefinitionService.updateWorkflowDefinition(user, projectCode, workflow.getName(),
                workflow.getCode(), request.getRemark() == null ? "" : request.getRemark(),
                "[]", "[]", 0, JSONUtils.toJsonString(Collections.singletonList(relation)),
                JSONUtils.toJsonString(Collections.singletonList(updated)), WorkflowExecutionTypeEnum.SERIAL_DISCARD);
        return getTask(user, projectCode, taskCode);
    }

    @Override
    @Transactional
    public DataSyncTaskVO publish(User user, long projectCode, long taskCode, DataSyncPublishRequest request) {
        authorizeWrite(user, projectCode);
        TaskDefinition definition = requireTask(projectCode, taskCode);
        if (request == null || request.getVersion() == null || request.getExpectedRevision() == null
                || request.getVersion() != definition.getVersion()
                || request.getExpectedRevision() != definition.getVersion()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "version");
        }
        validate(definition.getName(), JSONUtils.parseObject(definition.getTaskParams()), projectCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        workflowDefinitionService.onlineWorkflowDefinition(user, projectCode, workflow.getCode());
        return getTask(user, projectCode, taskCode);
    }

    @Override
    @Transactional
    public DataSyncTaskVO unpublish(User user, long projectCode, long taskCode) {
        authorizeWrite(user, projectCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        ensureStopped(workflow.getCode());
        workflowDefinitionService.offlineWorkflowDefinition(user, projectCode, workflow.getCode());
        return getTask(user, projectCode, taskCode);
    }

    @Override
    @Transactional
    public void deleteTask(User user, long projectCode, long taskCode) {
        authorizeWrite(user, projectCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        ensureStopped(workflow.getCode());
        if (workflow.getReleaseState() == ReleaseState.ONLINE) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_ALLOWED_EDIT, workflow.getCode());
        }
        TaskDefinition definition = requireTask(projectCode, taskCode);
        definition.setFlag(Flag.NO);
        workflow.setFlag(Flag.NO);
        if (!taskDefinitionDao.updateById(definition)) {
            throw new ServiceException(Status.UPDATE_TASK_DEFINITION_ERROR);
        }
        if (!workflowDefinitionDao.updateById(workflow)) {
            throw new ServiceException(Status.UPDATE_WORKFLOW_DEFINITION_ERROR);
        }
    }

    @Override
    public DataSyncTaskVersionVO getVersion(User user, long projectCode, long taskCode, int version) {
        authorizeRead(user, projectCode);
        requireTask(projectCode, taskCode, true);
        TaskDefinition historical = taskDefinitionDao.findTaskDefinition(taskCode, version);
        if (historical == null) {
            throw new ServiceException(Status.TASK_DEFINE_NOT_EXIST, taskCode);
        }
        return toTaskVersionVO(historical);
    }

    private static DataSyncTaskVersionVO toTaskVersionSummary(TaskDefinition definition) {
        return DataSyncTaskVersionVO.builder()
                .definitionId(String.valueOf(definition.getCode()))
                .version(definition.getVersion())
                .createBy(String.valueOf(definition.getUserId()))
                .createTime(definition.getCreateTime())
                .build();
    }

    private static DataSyncTaskVersionVO toTaskVersionVO(TaskDefinition definition) {
        DataSyncTaskVersionVO result = toTaskVersionSummary(definition);
        result.setParameters(JSONUtils.parseObject(definition.getTaskParams()));
        return result;
    }

    @Override
    public PageInfo<DataSyncInstanceVO> listInstances(User user, long projectCode, int pageNo,
                                                      int pageSize, Long taskCode, TaskExecutionStatus status) {
        authorizeRead(user, projectCode);
        if (taskCode != null) {
            requireTask(projectCode, taskCode, true);
        }
        IPage<TaskInstance> instances = taskInstanceDao.queryByProjectAndTaskType(
                new Page<>(pageNo, pageSize), projectCode, TASK_TYPE, taskCode,
                status == null ? null : new int[]{status.getCode()});
        List<DataSyncInstanceVO> rows = toInstanceVOs(instances.getRecords());
        PageInfo<DataSyncInstanceVO> result = PageInfo.of(pageNo, pageSize);
        result.setTotalList(rows);
        result.setTotal((int) instances.getTotal());
        return result;
    }

    @Override
    public List<DataSyncInstanceVO> latestInstances(User user, long projectCode,
                                                    List<Long> taskCodes, boolean activeOnly) {
        authorizeRead(user, projectCode);
        if (taskCodes == null || taskCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> requested = new HashSet<>(taskCodes);
        if (requested.contains(null)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskCodes");
        }
        List<TaskDefinition> definitions = taskDefinitionDao.queryByCodes(requested);
        if (definitions.size() != requested.size() || definitions.stream()
                .anyMatch(definition -> definition.getProjectCode() != projectCode
                        || !TASK_TYPE.equals(definition.getTaskType()))) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
        int[] states = activeOnly ? new int[]{
                TaskExecutionStatus.SUBMITTED_SUCCESS.getCode(),
                TaskExecutionStatus.DISPATCH.getCode(),
                TaskExecutionStatus.RUNNING_EXECUTION.getCode(),
                TaskExecutionStatus.DELAY_EXECUTION.getCode(),
                TaskExecutionStatus.NEED_FAULT_TOLERANCE.getCode()} : null;
        return toInstanceVOs(taskInstanceDao.queryLatestByTaskCodes(
                projectCode, TASK_TYPE, new ArrayList<>(requested), states));
    }

    @Override
    public DataSyncInstanceVO getInstance(User user, long projectCode, int instanceId) {
        TaskInstance instance = requireInstance(user, projectCode, instanceId);
        return toInstanceVO(instance, workflowInstanceDao.queryById(instance.getWorkflowInstanceId()));
    }

    @Override
    public DataSyncDefinitionVO getInstanceDefinition(User user, long projectCode, int instanceId) {
        TaskInstance instance = requireInstance(user, projectCode, instanceId);
        TaskDefinition definition = taskDefinitionDao.findTaskDefinition(
                instance.getTaskCode(), instance.getTaskDefinitionVersion());
        if (definition == null) {
            throw new ServiceException(Status.TASK_DEFINE_NOT_EXIST, instance.getTaskCode());
        }
        return new DataSyncDefinitionVO(String.valueOf(definition.getCode()), definition.getVersion(),
                definition.getName(), definition.getTaskType(),
                JSONUtils.parseObject(definition.getTaskParams()));
    }

    private void authorizeRead(User user, long projectCode) {
        projectService.checkProjectAndAuthThrowException(user, projectCode, null);
    }

    private void authorizeWrite(User user, long projectCode) {
        projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
    }

    private TaskDefinition requireTask(long projectCode, long taskCode) {
        return requireTask(projectCode, taskCode, false);
    }

    private TaskDefinition requireTask(long projectCode, long taskCode, boolean includeArchived) {
        TaskDefinition task = taskDefinitionDao.queryByCode(taskCode);
        if (task == null || task.getProjectCode() != projectCode || !TASK_TYPE.equals(task.getTaskType())
                || (!includeArchived && task.getFlag() == Flag.NO)) {
            throw new ServiceException(Status.TASK_DEFINE_NOT_EXIST, taskCode);
        }
        return task;
    }

    private WorkflowDefinition findWorkflow(long projectCode, long taskCode, boolean includeArchived) {
        WorkflowDefinition result = null;
        for (WorkflowTaskRelation relation : workflowTaskRelationDao.queryByTaskCode(taskCode)) {
            if (relation.getProjectCode() != projectCode || relation.getPreTaskCode() != 0
                    || relation.getPostTaskCode() != taskCode) {
                continue;
            }
            WorkflowDefinition workflow = workflowDefinitionDao.queryByCode(
                    relation.getWorkflowDefinitionCode()).orElse(null);
            if (workflow == null || workflow.getProjectCode() != projectCode
                    || (!includeArchived && workflow.getFlag() == Flag.NO)
                    || !("data_sync_" + taskCode).equals(workflow.getName())) {
                continue;
            }
            List<WorkflowTaskRelation> relations =
                    workflowTaskRelationDao.queryByWorkflowDefinitionCode(workflow.getCode());
            if (relations.size() != 1 || relations.get(0).getPostTaskCode() != taskCode) {
                continue;
            }
            if (result != null && result.getCode() != workflow.getCode()) {
                throw new ServiceException(Status.WORKFLOW_TASK_RELATION_NOT_EXIST, taskCode);
            }
            result = workflow;
        }
        return result;
    }

    private WorkflowDefinition requireWorkflow(long projectCode, long taskCode) {
        return requireWorkflow(projectCode, taskCode, false);
    }

    private WorkflowDefinition requireWorkflow(long projectCode, long taskCode, boolean includeArchived) {
        WorkflowDefinition workflow = findWorkflow(projectCode, taskCode, includeArchived);
        if (workflow == null) {
            throw new ServiceException(Status.WORKFLOW_TASK_RELATION_NOT_EXIST, taskCode);
        }
        return workflow;
    }

    private void ensureStopped(long workflowCode) {
        if (!workflowInstanceDao.queryByWorkflowDefinitionCodeAndStatus(
                workflowCode, WorkflowExecutionStatus.NOT_TERMINAL_STATES).isEmpty()) {
            throw new ServiceException(Status.WORKFLOW_INSTANCE_EXIST, workflowCode);
        }
    }

    private TaskInstance requireInstance(User user, long projectCode, int instanceId) {
        authorizeRead(user, projectCode);
        TaskInstance instance = taskInstanceDao.queryById(instanceId);
        if (instance == null || !Objects.equals(instance.getProjectCode(), projectCode)
                || !TASK_TYPE.equals(instance.getTaskType())) {
            throw new ServiceException(Status.TASK_INSTANCE_NOT_EXISTS, instanceId);
        }
        return instance;
    }

    private static DataSyncTaskVO toTaskVO(TaskDefinition task, WorkflowDefinition workflow) {
        return toTaskVO(task, workflow.getReleaseState(), workflow.getCode(),
                task.getFlag() == Flag.NO || workflow.getFlag() == Flag.NO);
    }

    private static DataSyncTaskVO toTaskVO(TaskDefinition task, ReleaseState status, long workflowCode,
                                           boolean archived) {
        boolean published = status == ReleaseState.ONLINE;
        return DataSyncTaskVO.builder()
                .id(String.valueOf(task.getCode()))
                .name(task.getName())
                .taskType(TASK_TYPE)
                .status(status)
                .workflowCode(String.valueOf(workflowCode))
                .archived(archived)
                .definitionVersion(task.getVersion())
                .revision(task.getVersion())
                .publishedVersion(published ? task.getVersion() : null)
                .parameters(JSONUtils.parseObject(task.getTaskParams()))
                .remark(task.getDescription())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .updateBy(String.valueOf(task.getUserId()))
                .build();
    }

    private List<DataSyncInstanceVO> toInstanceVOs(List<TaskInstance> instances) {
        Set<Integer> ids = instances.stream()
                .map(TaskInstance::getWorkflowInstanceId)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Integer, WorkflowInstance> workflows = ids.isEmpty() ? Collections.emptyMap()
                : workflowInstanceDao.queryByIds(ids).stream()
                        .collect(Collectors.toMap(WorkflowInstance::getId, Function.identity()));
        return instances.stream()
                .map(instance -> toInstanceVO(instance, workflows.get(instance.getWorkflowInstanceId())))
                .collect(Collectors.toList());
    }

    private static DataSyncInstanceVO toInstanceVO(TaskInstance instance, WorkflowInstance workflow) {
        return DataSyncInstanceVO.builder()
                .id(String.valueOf(instance.getId()))
                .definitionId(String.valueOf(instance.getTaskCode()))
                .taskName(instance.getName())
                .definitionVersion(instance.getTaskDefinitionVersion())
                .taskType(TASK_TYPE)
                .executeType(instance.getTaskExecuteType())
                .triggerType(workflow == null || workflow.getCommandType() == null ? null
                        : workflow.getCommandType().name())
                .status(instance.getState())
                .createTime(instance.getFirstSubmitTime())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .runtimeEngine("FLINK")
                .runtimeId(instance.getAppLink())
                .host(instance.getHost())
                .workflowInstanceId(instance.getWorkflowInstanceId())
                .build();
    }

    private DataSource[] validate(String name, JsonNode parameters, long projectCode) {
        if (name == null || name.trim().isEmpty()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "name");
        }
        if (parameters == null || !parameters.isObject()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parameters");
        }
        try {
            DataSyncParameters params = (DataSyncParameters) TaskPluginManager.parseTaskParameters(
                    TASK_TYPE, JSONUtils.toJsonString(parameters));
            params.validate();
        } catch (IllegalArgumentException | ClassCastException e) {
            String reason = e.getMessage();
            if (reason == null || reason.trim().isEmpty()) {
                reason = "invalid pipeline definition";
            }
            log.warn("Invalid DATA_SYNC parameters: {}", reason);
            throw new ServiceException(
                    Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parameters: " + reason);
        }
        JsonNode definition = parameters.path("definition");
        int sourceId = definition.path("source").path("datasource-id").asInt();
        int targetId = definition.path("sink").path("datasource-id").asInt();
        DataSource source = dataSourceDao.queryByIdAndProjectCode(sourceId, projectCode);
        DataSource sink = dataSourceDao.queryByIdAndProjectCode(targetId, projectCode);
        if (source == null || sink == null) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
        if (source.getType() != DbType.MYSQL || sink.getType() != DbType.MYSQL) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "datasource type");
        }
        return new DataSource[]{source, sink};
    }

    private static DataSourceParameters toDataSourceParameters(DataSource dataSource) {
        DataSourceParameters parameters = new DataSourceParameters();
        parameters.setType(dataSource.getType());
        parameters.setConnectionParams(dataSource.getConnectionParams());
        return parameters;
    }

    private static TaskExecuteType taskExecuteType(JsonNode parameters) {
        String runtimeMode = parameters.path("definition").path("pipeline")
                .path("execution.runtime-mode").asText();
        return "BATCH".equalsIgnoreCase(runtimeMode) ? TaskExecuteType.BATCH : TaskExecuteType.STREAM;
    }

    private static TaskDefinitionLog newTask(long code, User user, long projectCode, DataSyncTaskRequest request) {
        TaskDefinitionLog task = new TaskDefinitionLog();
        task.setCode(code);
        task.setName(request.getName().trim());
        task.setDescription(request.getRemark());
        task.setTaskType(TASK_TYPE);
        task.setTaskExecuteType(taskExecuteType(request.getParameters()));
        task.setTaskParams(JSONUtils.toJsonString(request.getParameters()));
        task.setProjectCode(projectCode);
        task.setUserId(user.getId());
        task.setWorkerGroup("default");
        task.setEnvironmentCode(-1L);
        task.setTaskPriority(Priority.MEDIUM);
        task.setTimeoutFlag(TimeoutFlag.CLOSE);
        task.setFlag(Flag.YES);
        task.setFailRetryTimes(0);
        return task;
    }

    private static WorkflowTaskRelationLog newRelation(long code, int version, String name) {
        WorkflowTaskRelationLog relation = new WorkflowTaskRelationLog();
        relation.setPreTaskCode(0);
        relation.setPostTaskCode(code);
        relation.setPostTaskVersion(version);
        relation.setName(name);
        return relation;
    }
}
