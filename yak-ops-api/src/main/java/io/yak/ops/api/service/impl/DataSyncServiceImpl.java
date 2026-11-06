package io.yak.ops.api.service.impl;

import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.enums.ExecuteType;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.DataSyncService;
import io.yak.ops.api.service.ExecutorService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.WorkflowDefinitionService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.DataSyncDefinitionVO;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.api.vo.DataSyncTaskVersionVO;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.TimeoutFlag;
import io.yak.ops.common.enums.WarningType;
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
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskDefinitionLogDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.common.ILogService;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryResponse;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.spi.enums.DbType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataSyncServiceImpl extends BaseServiceImpl implements DataSyncService {

    private static final String TASK_TYPE = "DATA_SYNC";
    private static final int PARAMETER_SCHEMA_VERSION = 8;
    private final ProjectService projectService;
    private final DataSourceDao dataSourceDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final TaskDefinitionLogDao taskDefinitionLogDao;
    private final TaskInstanceDao taskInstanceDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowInstanceDao workflowInstanceDao;
    private final WorkflowTaskRelationDao workflowTaskRelationDao;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final ExecutorService executorService;

    @Override
    public PageInfo<DataSyncTaskVO> listTasks(User user, long projectCode,
                                              int pageNo, int pageSize, String keyword, String status) {
        authorizeRead(user, projectCode);
        if (status != null && !"PUBLISHED".equals(status) && !"UNPUBLISHED".equals(status)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "status");
        }
        String search = keyword == null ? null : keyword.trim();
        Long taskCode = null;
        if (search != null && !search.isEmpty()) {
            try {
                taskCode = Long.valueOf(search);
            } catch (NumberFormatException ignored) {
                // Search by name when the input is not a task code.
            }
        }
        Integer releaseState = status == null ? null : "PUBLISHED".equals(status) ? 1 : 0;
        IPage<TaskDefinition> page = taskDefinitionDao.queryDataSyncPage(
                new Page<>(pageNo, pageSize), projectCode, TASK_TYPE, search, taskCode, releaseState);
        List<DataSyncTaskVO> rows = new ArrayList<>();
        if (!page.getRecords().isEmpty()) {
            Long[] taskCodes = page.getRecords().stream()
                    .map(TaskDefinition::getCode).toArray(Long[]::new);
            List<WorkflowTaskRelation> relations = workflowTaskRelationDao.queryByTaskCodes(taskCodes).stream()
                    .filter(relation -> relation.getProjectCode() == projectCode
                            && relation.getPreTaskCode() == 0)
                    .collect(Collectors.toList());
            Set<Long> workflowCodes = relations.stream()
                    .map(WorkflowTaskRelation::getWorkflowDefinitionCode)
                    .collect(Collectors.toSet());
            Map<Long, WorkflowDefinition> workflows = workflowCodes.isEmpty()
                    ? Collections.emptyMap()
                    : workflowDefinitionDao.queryByCodes(workflowCodes).stream()
                            .filter(workflow -> workflow.getProjectCode() == projectCode)
                            .collect(Collectors.toMap(WorkflowDefinition::getCode, workflow -> workflow));
            Map<Long, WorkflowDefinition> workflowsByTask = new HashMap<>();
            for (WorkflowTaskRelation relation : relations) {
                WorkflowDefinition workflow = workflows.get(relation.getWorkflowDefinitionCode());
                if (workflow != null && (releaseState == null
                        || workflow.getReleaseState().getCode() == releaseState)) {
                    workflowsByTask.putIfAbsent(relation.getPostTaskCode(), workflow);
                }
            }
            for (TaskDefinition definition : page.getRecords()) {
                WorkflowDefinition workflow = workflowsByTask.get(definition.getCode());
                if (workflow == null) {
                    throw new ServiceException(Status.WORKFLOW_TASK_RELATION_NOT_EXIST, definition.getCode());
                }
                rows.add(toTaskVO(definition, workflow));
            }
        }
        PageInfo<DataSyncTaskVO> result = PageInfo.of(pageNo, pageSize);
        result.setTotalList(rows);
        result.setTotal((int) page.getTotal());
        return result;
    }

    @Override
    public DataSyncTaskVO getTask(User user, long projectCode, long taskCode) {
        authorizeRead(user, projectCode);
        TaskDefinition definition = requireTask(projectCode, taskCode);
        return toTaskVO(definition, requireWorkflow(projectCode, taskCode));
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
                JSONUtils.toJsonString(Collections.singletonList(task)), "{}", WorkflowExecutionTypeEnum.PARALLEL);
        return toTaskVO(requireTask(projectCode, taskCode), workflow);
    }

    @Override
    @Transactional
    public DataSyncTaskVO updateTask(User user, long projectCode, long taskCode, DataSyncTaskRequest request) {
        authorizeWrite(user, projectCode);
        TaskDefinition original = requireTask(projectCode, taskCode);
        WorkflowDefinition workflow = lockWorkflow(projectCode, taskCode);
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
        WorkflowTaskRelationLog relation = newRelation(taskCode, original.getVersion(), updated.getName());
        workflowDefinitionService.updateWorkflowDefinition(user, projectCode, workflow.getName(),
                workflow.getCode(), request.getRemark() == null ? "" : request.getRemark(),
                "[]", "[]", 0, JSONUtils.toJsonString(Collections.singletonList(relation)),
                JSONUtils.toJsonString(Collections.singletonList(updated)), WorkflowExecutionTypeEnum.PARALLEL);
        return getTask(user, projectCode, taskCode);
    }

    @Override
    @Transactional
    public DataSyncTaskVO publish(User user, long projectCode, long taskCode) {
        authorizeWrite(user, projectCode);
        TaskDefinition definition = requireTask(projectCode, taskCode);
        validate(definition.getName(), JSONUtils.parseObject(definition.getTaskParams()), projectCode);
        WorkflowDefinition workflow = lockWorkflow(projectCode, taskCode);
        workflowDefinitionService.onlineWorkflowDefinition(user, projectCode, workflow.getCode());
        return getTask(user, projectCode, taskCode);
    }

    @Override
    @Transactional
    public DataSyncTaskVO unpublish(User user, long projectCode, long taskCode) {
        authorizeWrite(user, projectCode);
        WorkflowDefinition workflow = lockWorkflow(projectCode, taskCode);
        ensureStopped(workflow.getCode());
        workflowDefinitionService.offlineWorkflowDefinition(user, projectCode, workflow.getCode());
        return getTask(user, projectCode, taskCode);
    }

    @Override
    @Transactional
    public void deleteTask(User user, long projectCode, long taskCode) {
        authorizeWrite(user, projectCode);
        WorkflowDefinition workflow = lockWorkflow(projectCode, taskCode);
        ensureStopped(workflow.getCode());
        if (workflow.getReleaseState() == ReleaseState.ONLINE) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_ALLOWED_EDIT, workflow.getCode());
        }
        workflowDefinitionService.deleteWorkflowDefinitionByCode(user, workflow.getCode());
    }

    @Override
    public PageInfo<DataSyncTaskVersionVO> listVersions(User user, long projectCode, long taskCode,
                                                        int pageNo, int pageSize) {
        authorizeRead(user, projectCode);
        requireTask(projectCode, taskCode);
        IPage<TaskDefinitionLog> page = taskDefinitionLogDao.queryVersionsPaging(
                new Page<>(pageNo, pageSize), taskCode, projectCode);
        PageInfo<DataSyncTaskVersionVO> result = PageInfo.of(pageNo, pageSize);
        result.setTotalList(page.getRecords().stream()
                .map(DataSyncServiceImpl::toTaskVersionSummary).collect(Collectors.toList()));
        result.setTotal((int) page.getTotal());
        return result;
    }

    @Override
    public DataSyncTaskVersionVO getVersion(User user, long projectCode, long taskCode, int version) {
        authorizeRead(user, projectCode);
        requireTask(projectCode, taskCode);
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
                .parameterSchemaVersion(PARAMETER_SCHEMA_VERSION)
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
    @Transactional
    public Integer start(User user, long projectCode, long taskCode) {
        authorizeWrite(user, projectCode);
        TaskDefinition definition = requireTask(projectCode, taskCode);
        validate(definition.getName(), JSONUtils.parseObject(definition.getTaskParams()), projectCode);
        WorkflowDefinition workflow = lockWorkflow(projectCode, taskCode);
        if (workflow.getReleaseState() != ReleaseState.ONLINE) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_RELEASE, workflow.getCode(),
                    definition.getVersion());
        }
        ensureStopped(workflow.getCode());
        Integer runId = executorService.triggerWorkflowDefinition(
                io.yak.ops.api.dto.workflow.WorkflowTriggerRequest.builder()
                        .loginUser(user)
                        .projectCode(projectCode)
                        .workflowDefinitionCode(workflow.getCode())
                        .execType(CommandType.START_PROCESS)
                        .failureStrategy(FailureStrategy.END)
                        .taskDependType(TaskDependType.TASK_POST)
                        .warningType(WarningType.NONE)
                        .workflowInstancePriority(Priority.MEDIUM)
                        .workerGroup(definition.getWorkerGroup())
                        .tenantCode("default")
                        .environmentCode(definition.getEnvironmentCode())
                        .dryRun(Flag.NO)
                        .build());
        return runId;
    }

    @Override
    public PageInfo<DataSyncInstanceVO> listInstances(User user, long projectCode, int pageNo,
                                                      int pageSize, Long taskCode, String status) {
        authorizeRead(user, projectCode);
        if (taskCode != null) {
            requireTask(projectCode, taskCode);
        }
        IPage<TaskInstance> instances = taskInstanceDao.queryByProjectAndTaskType(
                new Page<>(pageNo, pageSize), projectCode, TASK_TYPE, taskCode, taskStates(status));
        List<DataSyncInstanceVO> rows = instances.getRecords().stream()
                .map(DataSyncServiceImpl::toInstanceVO)
                .collect(Collectors.toList());
        PageInfo<DataSyncInstanceVO> result = PageInfo.of(pageNo, pageSize);
        result.setTotalList(rows);
        result.setTotal((int) instances.getTotal());
        return result;
    }

    private static int[] taskStates(String status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        switch (status) {
            case "SUBMITTED":
                return new int[]{TaskExecutionStatus.SUBMITTED_SUCCESS.getCode()};
            case "LAUNCHING":
                return new int[]{TaskExecutionStatus.DISPATCH.getCode()};
            case "RUNNING":
                return new int[]{TaskExecutionStatus.RUNNING_EXECUTION.getCode()};
            case "FINISHED":
                return new int[]{TaskExecutionStatus.SUCCESS.getCode(), TaskExecutionStatus.FORCED_SUCCESS.getCode()};
            case "FAILED":
                return new int[]{TaskExecutionStatus.FAILURE.getCode()};
            case "CANCELED":
                return new int[]{TaskExecutionStatus.KILL.getCode()};
            case "RECONCILING":
                return new int[]{TaskExecutionStatus.NEED_FAULT_TOLERANCE.getCode()};
            case "CANCELLING":
                return new int[]{-1};
            default:
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "status");
        }
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
                TaskExecutionStatus.NEED_FAULT_TOLERANCE.getCode()} : null;
        return taskInstanceDao.queryLatestByTaskCodes(
                projectCode, TASK_TYPE, new ArrayList<>(requested), states).stream()
                .map(DataSyncServiceImpl::toInstanceVO)
                .collect(Collectors.toList());
    }

    @Override
    public DataSyncInstanceVO getInstance(User user, long projectCode, int instanceId) {
        return toInstanceVO(requireInstance(user, projectCode, instanceId));
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
                definition.getName(), definition.getTaskType(), PARAMETER_SCHEMA_VERSION,
                JSONUtils.parseObject(definition.getTaskParams()));
    }

    @Override
    public void stop(User user, long projectCode, int instanceId) {
        authorizeWrite(user, projectCode);
        TaskInstance instance = requireInstance(user, projectCode, instanceId);
        if (!active(toInstanceVO(instance).getStatus())) {
            return;
        }
        executorService.controlWorkflowInstance(user, instance.getWorkflowInstanceId(), ExecuteType.STOP);
    }

    @Override
    public Map<String, Object> logs(User user, long projectCode, int instanceId, int offset, int limit) {
        TaskInstance instance = requireInstance(user, projectCode, instanceId);
        if (instance.getHost() == null || instance.getLogPath() == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("content", "");
            result.put("nextOffset", Math.max(0, offset));
            result.put("hasMore", false);
            return result;
        }
        int start = Math.max(0, offset);
        TaskInstanceLogPageQueryResponse page = Clients.withService(ILogService.class)
                .withHost(instance.getHost())
                .pageQueryTaskInstanceLog(TaskInstanceLogPageQueryRequest.builder()
                        .taskInstanceId(instanceId)
                        .taskInstanceLogAbsolutePath(instance.getLogPath())
                        .skipLineNum(start)
                        .limit(Math.max(1, Math.min(limit, 500)))
                        .build());
        String content = page.getLogContent() == null ? "" : page.getLogContent();
        int count = content.isEmpty() ? 0 : content.split("\\r?\\n", -1).length;
        if (content.endsWith("\n")) {
            count--;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("nextOffset", start + count);
        result.put("hasMore", count >= Math.max(1, Math.min(limit, 500)));
        return result;
    }

    private void authorizeRead(User user, long projectCode) {
        projectService.checkProjectAndAuthThrowException(user, projectCode, null);
    }

    private void authorizeWrite(User user, long projectCode) {
        projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
    }

    private TaskDefinition requireTask(long projectCode, long taskCode) {
        TaskDefinition task = taskDefinitionDao.queryByCode(taskCode);
        if (task == null || task.getProjectCode() != projectCode || !TASK_TYPE.equals(task.getTaskType())) {
            throw new ServiceException(Status.TASK_DEFINE_NOT_EXIST, taskCode);
        }
        return task;
    }

    private WorkflowDefinition findWorkflow(long projectCode, long taskCode) {
        for (WorkflowTaskRelation relation : workflowTaskRelationDao.queryByTaskCode(taskCode)) {
            if (relation.getPreTaskCode() != 0 || relation.getPostTaskCode() != taskCode) {
                continue;
            }
            WorkflowDefinition workflow = workflowDefinitionDao.queryByCode(
                    relation.getWorkflowDefinitionCode()).orElse(null);
            if (workflow != null && workflow.getProjectCode() == projectCode) {
                List<WorkflowTaskRelation> relations =
                        workflowTaskRelationDao.queryByWorkflowDefinitionCode(workflow.getCode());
                if (relations.size() == 1 && relations.get(0).getPostTaskCode() == taskCode) {
                    return workflow;
                }
            }
        }
        return null;
    }

    private WorkflowDefinition lockWorkflow(long projectCode, long taskCode) {
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        workflowDefinitionDao.lockByCode(workflow.getCode());
        return workflowDefinitionDao.queryByCode(workflow.getCode())
                .orElseThrow(() -> new ServiceException(Status.WORKFLOW_DEFINITION_NOT_EXIST, workflow.getCode()));
    }

    private WorkflowDefinition requireWorkflow(long projectCode, long taskCode) {
        WorkflowDefinition workflow = findWorkflow(projectCode, taskCode);
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
        boolean published = workflow.getReleaseState() == ReleaseState.ONLINE;
        return DataSyncTaskVO.builder()
                .id(String.valueOf(task.getCode()))
                .name(task.getName())
                .taskType(TASK_TYPE)
                .status(published ? "PUBLISHED" : "UNPUBLISHED")
                .definitionVersion(task.getVersion())
                .revision(task.getVersion())
                .publishedVersion(published ? task.getVersion() : null)
                .publishedDebug(published ? false : null)
                .hasUnpublishedChanges(false)
                .parameterSchemaVersion(PARAMETER_SCHEMA_VERSION)
                .parameters(JSONUtils.parseObject(task.getTaskParams()))
                .debug(false)
                .remark(task.getDescription())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .updateBy(String.valueOf(task.getUserId()))
                .build();
    }

    private static DataSyncInstanceVO toInstanceVO(TaskInstance instance) {
        return DataSyncInstanceVO.builder()
                .id(String.valueOf(instance.getId()))
                .definitionId(String.valueOf(instance.getTaskCode()))
                .taskName(instance.getName())
                .definitionVersion(instance.getTaskDefinitionVersion())
                .taskType(TASK_TYPE)
                .triggerType("MANUAL")
                .status(status(instance))
                .createTime(instance.getFirstSubmitTime())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .runtimeEngine("FLINK")
                .runtimeId(instance.getAppLink())
                .host(instance.getHost())
                .workflowInstanceId(instance.getWorkflowInstanceId())
                .build();
    }

    private static String status(TaskInstance instance) {
        switch (instance.getState()) {
            case SUBMITTED_SUCCESS:
                return "SUBMITTED";
            case DISPATCH:
                return "LAUNCHING";
            case RUNNING_EXECUTION:
                return "RUNNING";
            case SUCCESS:
                return "FINISHED";
            case FAILURE:
                return "FAILED";
            case KILL:
                return "CANCELED";
            case NEED_FAULT_TOLERANCE:
                return "RECONCILING";
            default:
                return instance.getState().name();
        }
    }

    private static boolean active(String status) {
        return "SUBMITTED".equals(status) || "LAUNCHING".equals(status)
                || "RUNNING".equals(status) || "CANCELLING".equals(status)
                || "RECONCILING".equals(status);
    }

    private void validate(String name, JsonNode parameters, long projectCode) {
        if (name == null || name.trim().isEmpty()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "name");
        }
        if (parameters == null || !parameters.isObject()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parameters");
        }
        rejectCredentials(parameters);
        AbstractParameters params = TaskPluginManager.parseTaskParameters(TASK_TYPE,
                JSONUtils.toJsonString(parameters));
        if (params == null || !params.checkParameters()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parameters");
        }
        int sourceId = parameters.path("source").path("datasourceId").asInt();
        int targetId = parameters.path("sink").path("datasourceId").asInt();
        DataSource source = dataSourceDao.queryByIdAndProjectCode(sourceId, projectCode);
        DataSource sink = dataSourceDao.queryByIdAndProjectCode(targetId, projectCode);
        if (source == null || sink == null || source.getType() != DbType.MYSQL || sink.getType() != DbType.MYSQL) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
    }

    private static void rejectCredentials(JsonNode parameters) {
        if (parameters.isArray()) {
            for (JsonNode item : parameters) {
                rejectCredentials(item);
            }
            return;
        }
        if (!parameters.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = parameters.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
            if ("password".equals(key) || "passwd".equals(key) || "token".equals(key)
                    || "secret".equals(key) || "secretkey".equals(key)
                    || "accesskey".equals(key) || "apikey".equals(key)
                    || "privatekey".equals(key) || "credentials".equals(key)) {
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, field.getKey());
            }
            rejectCredentials(field.getValue());
        }
    }

    private static TaskDefinitionLog newTask(long code, User user, long projectCode, DataSyncTaskRequest request) {
        TaskDefinitionLog task = new TaskDefinitionLog();
        task.setCode(code);
        task.setName(request.getName().trim());
        task.setDescription(request.getRemark());
        task.setTaskType(TASK_TYPE);
        task.setTaskExecuteType(TaskExecuteType.STREAM);
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
