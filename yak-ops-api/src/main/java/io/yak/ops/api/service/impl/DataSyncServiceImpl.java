package io.yak.ops.api.service.impl;

import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.enums.ExecuteType;
import io.yak.ops.api.service.DataSyncService;
import io.yak.ops.api.service.ExecutorService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.WorkflowDefinitionService;
import io.yak.ops.api.vo.DataSyncDefinitionVO;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.api.vo.DataSyncLogVO;
import io.yak.ops.api.vo.DataSyncPageVO;
import io.yak.ops.api.vo.DataSyncRunVO;
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
import io.yak.ops.dao.model.WorkflowInstanceSummaryDto;
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
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.spi.enums.DbType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataSyncServiceImpl implements DataSyncService {

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
    public DataSyncPageVO<DataSyncTaskVO> listTasks(User user, long projectCode,
                                                      int pageNo, int pageSize, String keyword, String status) {
        authorize(user, projectCode);
        List<DataSyncTaskVO> rows = new ArrayList<>();
        for (TaskDefinition definition : taskDefinitionDao.queryByProjectAndType(projectCode, TASK_TYPE)) {
            WorkflowDefinition workflow = findWorkflow(projectCode, definition.getCode());
            if (workflow == null) {
                continue;
            }
            DataSyncTaskVO row = toTaskVO(definition, workflow);
            if (keyword != null && !keyword.trim().isEmpty()
                    && !row.getName().toLowerCase().contains(keyword.trim().toLowerCase())
                    && !row.getId().contains(keyword.trim())) {
                continue;
            }
            if (status == null || status.equals(row.getStatus())) {
                rows.add(row);
            }
        }
        return DataSyncPageVO.of(rows, pageNo, pageSize);
    }

    @Override
    public DataSyncTaskVO getTask(User user, long projectCode, long taskCode) {
        authorize(user, projectCode);
        TaskDefinition definition = requireTask(projectCode, taskCode);
        return toTaskVO(definition, requireWorkflow(projectCode, taskCode));
    }

    @Override
    @Transactional
    public DataSyncTaskVO createTask(User user, long projectCode, DataSyncTaskRequest request) {
        authorize(user, projectCode);
        validate(request.getName(), request.getParameters());
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
        authorize(user, projectCode);
        TaskDefinition original = requireTask(projectCode, taskCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        if (workflow.getReleaseState() == ReleaseState.ONLINE) {
            throw new IllegalStateException("Unpublish the data synchronization task before editing");
        }
        if (request.getExpectedRevision() == null || request.getExpectedRevision() != original.getVersion()) {
            throw new IllegalStateException("Task definition has changed; reload before updating");
        }
        JsonNode parameters = request.getParameters() == null
                ? JSONUtils.parseObject(original.getTaskParams()) : request.getParameters();
        validate(request.getName(), parameters);
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
    public DataSyncTaskVO publish(User user, long projectCode, long taskCode) {
        authorize(user, projectCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        workflowDefinitionService.onlineWorkflowDefinition(user, projectCode, workflow.getCode());
        return getTask(user, projectCode, taskCode);
    }

    @Override
    public DataSyncTaskVO unpublish(User user, long projectCode, long taskCode) {
        authorize(user, projectCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        ensureStopped(workflow.getCode());
        workflowDefinitionService.offlineWorkflowDefinition(user, projectCode, workflow.getCode());
        return getTask(user, projectCode, taskCode);
    }

    @Override
    public void deleteTask(User user, long projectCode, long taskCode) {
        authorize(user, projectCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        ensureStopped(workflow.getCode());
        if (workflow.getReleaseState() == ReleaseState.ONLINE) {
            throw new IllegalStateException("Unpublish the task before deletion");
        }
        workflowDefinitionService.deleteWorkflowDefinitionByCode(user, workflow.getCode());
    }

    @Override
    public DataSyncPageVO<DataSyncTaskVersionVO> listVersions(User user, long projectCode, long taskCode,
                                                                int pageNo, int pageSize) {
        authorize(user, projectCode);
        requireTask(projectCode, taskCode);
        List<DataSyncTaskVersionVO> versions = taskDefinitionLogDao.queryVersions(taskCode)
                .stream().map(DataSyncServiceImpl::toTaskVersionVO).collect(Collectors.toList());
        return DataSyncPageVO.of(versions, pageNo, pageSize);
    }

    @Override
    public DataSyncTaskVersionVO getVersion(User user, long projectCode, long taskCode, int version) {
        authorize(user, projectCode);
        requireTask(projectCode, taskCode);
        TaskDefinition historical = taskDefinitionDao.findTaskDefinition(taskCode, version);
        if (historical == null) {
            throw new IllegalArgumentException("Task version not found");
        }
        return toTaskVersionVO(historical);
    }

    private static DataSyncTaskVersionVO toTaskVersionVO(TaskDefinition definition) {
        return DataSyncTaskVersionVO.builder()
                .definitionId(String.valueOf(definition.getCode()))
                .version(definition.getVersion())
                .parameterSchemaVersion(PARAMETER_SCHEMA_VERSION)
                .createBy(String.valueOf(definition.getUserId()))
                .createTime(definition.getCreateTime())
                .parameters(visibleParameters(JSONUtils.parseObject(definition.getTaskParams())))
                .build();
    }

    @Override
    public DataSyncRunVO start(User user, long projectCode, long taskCode) {
        authorize(user, projectCode);
        TaskDefinition definition = requireTask(projectCode, taskCode);
        WorkflowDefinition workflow = requireWorkflow(projectCode, taskCode);
        if (workflow.getReleaseState() != ReleaseState.ONLINE) {
            throw new IllegalStateException("Publish the data synchronization task before starting");
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
        return new DataSyncRunVO(String.valueOf(taskCode), runId, definition.getVersion(), "SUBMITTED");
    }

    @Override
    public DataSyncPageVO<DataSyncInstanceVO> listInstances(User user, long projectCode, int pageNo,
                                                               int pageSize, Long taskCode, String status) {
        authorize(user, projectCode);
        List<DataSyncInstanceVO> list = new ArrayList<>();
        List<TaskDefinition> definitions = taskCode == null
                ? taskDefinitionDao.queryByProjectAndType(projectCode, TASK_TYPE)
                : Collections.singletonList(requireTask(projectCode, taskCode));
        for (TaskDefinition definition : definitions) {
            WorkflowDefinition workflow = findWorkflow(projectCode, definition.getCode());
            if (workflow == null) {
                continue;
            }
            for (WorkflowInstanceSummaryDto run : workflowInstanceDao.queryByWorkflowDefinitionCode(workflow.getCode(), 100)) {
                for (TaskInstance instance : taskInstanceDao.queryByWorkflowInstanceId(run.getId())) {
                    if (!TASK_TYPE.equals(instance.getTaskType())) {
                        continue;
                    }
                    DataSyncInstanceVO row = toInstanceVO(instance);
                    if (status == null || status.equals(row.getStatus())) {
                        list.add(row);
                    }
                }
            }
        }
        list.sort(Comparator.comparing(DataSyncInstanceVO::getId, Comparator.comparingLong(Long::parseLong)).reversed());
        return DataSyncPageVO.of(list, pageNo, pageSize);
    }

    @Override
    public List<DataSyncInstanceVO> latestInstances(User user, long projectCode,
                                                      List<Long> taskCodes, boolean activeOnly) {
        authorize(user, projectCode);
        List<DataSyncInstanceVO> rows = new ArrayList<>();
        for (Long taskCode : taskCodes) {
            DataSyncPageVO<DataSyncInstanceVO> page =
                    listInstances(user, projectCode, 1, 50, taskCode, null);
            for (DataSyncInstanceVO instance : page.getBizData()) {
                if (!activeOnly || active(instance.getStatus())) {
                    rows.add(instance);
                    break;
                }
            }
        }
        return rows;
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
            throw new IllegalArgumentException("Task definition version not found");
        }
        return new DataSyncDefinitionVO(String.valueOf(definition.getCode()), definition.getVersion(),
                definition.getName(), definition.getTaskType(), PARAMETER_SCHEMA_VERSION,
                visibleParameters(JSONUtils.parseObject(definition.getTaskParams())));
    }

    @Override
    public void stop(User user, long projectCode, int instanceId) {
        TaskInstance instance = requireInstance(user, projectCode, instanceId);
        if (!active(toInstanceVO(instance).getStatus())) {
            return;
        }
        executorService.controlWorkflowInstance(user, instance.getWorkflowInstanceId(), ExecuteType.STOP);
    }

    @Override
    public DataSyncLogVO logs(User user, long projectCode, int instanceId, int offset, int limit) {
        TaskInstance instance = requireInstance(user, projectCode, instanceId);
        if (instance.getHost() == null || instance.getLogPath() == null) {
            return new DataSyncLogVO("", Math.max(0, offset), false);
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
        return new DataSyncLogVO(content, start + count, count >= Math.max(1, Math.min(limit, 500)));
    }

    private void authorize(User user, long projectCode) {
        projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
    }

    private TaskDefinition requireTask(long projectCode, long taskCode) {
        TaskDefinition task = taskDefinitionDao.queryByCode(taskCode);
        if (task == null || task.getProjectCode() != projectCode || !TASK_TYPE.equals(task.getTaskType())) {
            throw new IllegalArgumentException("Data synchronization task not found: " + taskCode);
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

    private WorkflowDefinition requireWorkflow(long projectCode, long taskCode) {
        WorkflowDefinition workflow = findWorkflow(projectCode, taskCode);
        if (workflow == null) {
            throw new IllegalArgumentException("Execution mapping not found for task: " + taskCode);
        }
        return workflow;
    }

    private void ensureStopped(long workflowCode) {
        for (WorkflowInstanceSummaryDto run : workflowInstanceDao.queryByWorkflowDefinitionCode(workflowCode, 100)) {
            if (!run.getState().isFinalState()) {
                throw new IllegalStateException("Data synchronization is already running");
            }
        }
    }

    private TaskInstance requireInstance(User user, long projectCode, int instanceId) {
        authorize(user, projectCode);
        TaskInstance instance = taskInstanceDao.queryById(instanceId);
        if (instance == null || !Objects.equals(instance.getProjectCode(), projectCode)
                || !TASK_TYPE.equals(instance.getTaskType())) {
            throw new IllegalArgumentException("Data synchronization instance not found");
        }
        requireWorkflow(projectCode, instance.getTaskCode());
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
                .parameters(visibleParameters(JSONUtils.parseObject(task.getTaskParams())))
                .debug(false)
                .remark(task.getDescription())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .updateBy(String.valueOf(task.getUserId()))
                .build();
    }

    private static JsonNode visibleParameters(JsonNode original) {
        // Convert the older first-iteration single-table structure without changing stored versions.
        if (original.has("route") && original.path("sink").has("schemaPolicy")) {
            return original;
        }
        ObjectNode parameters = original.deepCopy();
        ObjectNode source = (ObjectNode) parameters.path("source");
        ObjectNode sink = (ObjectNode) parameters.path("sink");
        source.put("type", "mysql-cdc");
        sink.put("type", "jdbc-mysql");
        ObjectNode policy = sink.putObject("schemaPolicy");
        policy.put("initial", "CREATE_OR_ALTER");
        ObjectNode evolution = policy.putObject("evolution");
        evolution.put("enabled", true);
        evolution.put("allowDropColumns", false);
        ObjectNode write = (ObjectNode) sink.path("tables").get(0);
        write.putObject("write").put("mode", "UPSERT");
        ObjectNode route = parameters.putArray("route").addObject();
        JsonNode sourceTable = source.path("tables").get(0);
        JsonNode sinkTable = sink.path("tables").get(0);
        route.put("source-table", sourceTable.path("database").asText() + "." + sourceTable.path("table").asText());
        route.put("sink-table", sinkTable.path("database").asText() + "." + sinkTable.path("table").asText());
        parameters.putArray("transform");
        ((ObjectNode) parameters.path("pipeline")).put("pipeline.table-scope", "SINGLE");
        return parameters;
    }

    private static DataSyncInstanceVO toInstanceVO(TaskInstance instance) {
        return DataSyncInstanceVO.builder()
                .id(String.valueOf(instance.getId()))
                .definitionId(String.valueOf(instance.getTaskCode()))
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

    private void validate(String name, JsonNode parameters) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Task name must not be empty");
        }
        if (parameters == null || !parameters.isObject()) {
            throw new IllegalArgumentException("Data synchronization parameters are required");
        }
        AbstractParameters params = TaskPluginManager.parseTaskParameters(TASK_TYPE,
                JSONUtils.toJsonString(parameters));
        if (params == null || !params.checkParameters()) {
            throw new IllegalArgumentException("Unsupported DATA_SYNC configuration: requires a MySQL table with initial snapshot and streaming updates");
        }
        int sourceId = parameters.path("source").path("datasourceId").asInt();
        int targetId = parameters.path("sink").path("datasourceId").asInt();
        DataSource source = dataSourceDao.queryById(sourceId);
        DataSource sink = dataSourceDao.queryById(targetId);
        if (source == null || sink == null || source.getType() != DbType.MYSQL || sink.getType() != DbType.MYSQL) {
            throw new IllegalArgumentException("The initial DATA_SYNC connector supports MySQL to MySQL only");
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
