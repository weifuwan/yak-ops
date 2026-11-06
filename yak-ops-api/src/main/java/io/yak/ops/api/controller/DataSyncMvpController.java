package io.yak.ops.api.controller;

import io.yak.ops.api.enums.ExecuteType;
import io.yak.ops.api.service.ExecutorService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.WorkflowDefinitionService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.TimeoutFlag;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.enums.WorkflowExecutionTypeEnum;
import io.yak.ops.common.utils.CodeGenerateUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.model.WorkflowInstanceSummaryDto;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.common.ILogService;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryResponse;
import io.yak.ops.spi.enums.DbType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;

/**
 * Small UI adapter for the first single-table CDC milestone.
 * A single-node Workflow lets the existing Master, Worker and event bus own execution.
 * No cron triggers or independent execution engine are created here.
 */
@RestController
@RequestMapping("/api/v1/data-sync/mvp")
public class DataSyncMvpController {

    private static final String WORKFLOW_PREFIX = "__yak_ops_data_sync__";

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectDao projectDao;
    @Autowired
    private DataSourceDao dataSourceDao;
    @Autowired
    private TaskDefinitionDao taskDefinitionDao;
    @Autowired
    private TaskInstanceDao taskInstanceDao;
    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;
    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;
    @Autowired
    private WorkflowTaskRelationDao workflowTaskRelationDao;
    @Autowired
    private WorkflowDefinitionService workflowDefinitionService;
    @Autowired
    private ExecutorService executorService;

    @GetMapping("/projects")
    public Result<List<Map<String, String>>> projects(
                                                      @RequestAttribute(Constants.SESSION_USER) User user) {
        List<Long> codes = projectService.getAuthorizedProjectCodes(user);
        List<Map<String, String>> result = new ArrayList<>();
        for (Project project : projectDao.queryByCodes(codes)) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", String.valueOf(project.getCode()));
            item.put("name", project.getName());
            result.add(item);
        }
        return Result.success(result);
    }

    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> tasks(
                                                   @RequestAttribute(Constants.SESSION_USER) User user,
                                                   @RequestParam("projectCode") long projectCode) {
        projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (WorkflowDefinition workflow : workflowDefinitionDao.queryAllDefinitionList(projectCode)) {
            if (!workflow.getName().startsWith(WORKFLOW_PREFIX)) {
                continue;
            }
            TaskDefinition definition = getTask(workflow.getCode());
            if (definition != null && "DATA_SYNC".equals(definition.getTaskType())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", String.valueOf(workflow.getCode()));
                item.put("taskCode", String.valueOf(definition.getCode()));
                item.put("name", definition.getName());
                item.put("version", definition.getVersion());
                item.put("projectCode", String.valueOf(projectCode));
                item.put("taskType", definition.getTaskType());
                item.put("parameters", JSONUtils.parseObject(definition.getTaskParams()));
                tasks.add(item);
            }
        }
        return Result.success(tasks);
    }

    @PostMapping("/tasks")
    public Result<Map<String, String>> create(
                                              @RequestAttribute(Constants.SESSION_USER) User user,
                                              @RequestBody CreateRequest request) {
        projectService.checkHasProjectWritePermissionThrowException(user, request.getProjectCode());
        requireText(request.getName(), "Task name");
        requireText(request.getSourceDatabase(), "Source database");
        requireText(request.getSourceTable(), "Source table");
        requireText(request.getTargetDatabase(), "Target database");
        requireText(request.getTargetTable(), "Target table");

        DataSource source = dataSourceDao.queryById(request.getSourceDataSourceId());
        DataSource target = dataSourceDao.queryById(request.getTargetDataSourceId());
        if (source == null || target == null || source.getType() != DbType.MYSQL || target.getType() != DbType.MYSQL) {
            throw new IllegalArgumentException("Both saved datasources must be MySQL");
        }

        Map<String, Object> startup = new LinkedHashMap<>();
        startup.put("mode", "INITIAL");
        Map<String, Object> sourceConfig = endpoint("mysql", request.getSourceDataSourceId(),
                request.getSourceDatabase(), request.getSourceTable());
        sourceConfig.put("startup", startup);
        Map<String, Object> sinkConfig = endpoint("jdbc", request.getTargetDataSourceId(),
                request.getTargetDatabase(), request.getTargetTable());

        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("execution.runtime-mode", "STREAMING");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("source", sourceConfig);
        parameters.put("sink", sinkConfig);
        parameters.put("pipeline", pipeline);

        long taskCode = CodeGenerateUtils.genCode();
        TaskDefinitionLog definition = new TaskDefinitionLog();
        definition.setCode(taskCode);
        definition.setName(request.getName().trim());
        definition.setTaskType("DATA_SYNC");
        definition.setTaskExecuteType(TaskExecuteType.STREAM);
        definition.setTaskParams(JSONUtils.toJsonString(parameters));
        definition.setProjectCode(request.getProjectCode());
        definition.setUserId(user.getId());
        definition.setWorkerGroup("default");
        definition.setEnvironmentCode(-1L);
        definition.setTaskPriority(Priority.MEDIUM);
        definition.setTimeoutFlag(TimeoutFlag.CLOSE);
        definition.setFlag(Flag.YES);
        definition.setFailRetryTimes(0);

        WorkflowTaskRelationLog relation = new WorkflowTaskRelationLog();
        relation.setPreTaskCode(0);
        relation.setPostTaskCode(taskCode);
        relation.setPostTaskVersion(Constants.VERSION_FIRST);
        relation.setName(definition.getName());

        WorkflowDefinition workflow = workflowDefinitionService.createWorkflowDefinition(
                user, request.getProjectCode(), WORKFLOW_PREFIX + taskCode,
                "", "[]", "[]", 0,
                JSONUtils.toJsonString(Collections.singletonList(relation)),
                JSONUtils.toJsonString(Collections.singletonList(definition)),
                "{}", WorkflowExecutionTypeEnum.PARALLEL);
        workflowDefinitionService.onlineWorkflowDefinition(user, request.getProjectCode(), workflow.getCode());

        Map<String, String> saved = new LinkedHashMap<>();
        saved.put("id", String.valueOf(workflow.getCode()));
        saved.put("taskCode", String.valueOf(taskCode));
        return Result.success(saved);
    }

    @PostMapping("/tasks/{id}/start")
    public Result<Map<String, String>> start(
                                             @RequestAttribute(Constants.SESSION_USER) User user,
                                             @PathVariable("id") long workflowCode,
                                             @RequestParam("projectCode") long projectCode) {
        TaskDefinition definition = authorizedTask(user, projectCode, workflowCode);
        List<WorkflowInstanceSummaryDto> history =
                workflowInstanceDao.queryByWorkflowDefinitionCode(workflowCode, 20);
        for (WorkflowInstanceSummaryDto previous : history) {
            if (!previous.getState().isFinalState()) {
                throw new IllegalStateException("This synchronization task is already running");
            }
        }
        Integer workflowInstanceId = executorService.triggerWorkflowDefinition(
                io.yak.ops.api.dto.workflow.WorkflowTriggerRequest.builder()
                        .loginUser(user)
                        .projectCode(projectCode)
                        .workflowDefinitionCode(workflowCode)
                        .execType(CommandType.START_PROCESS)
                        .failureStrategy(FailureStrategy.END)
                        .taskDependType(TaskDependType.TASK_POST)
                        .warningType(WarningType.NONE)
                        .workflowInstancePriority(Priority.MEDIUM)
                        .workerGroup("default")
                        .tenantCode("default")
                        .environmentCode(-1L)
                        .dryRun(Flag.NO)
                        .build());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("workflowInstanceId", String.valueOf(workflowInstanceId));
        result.put("taskCode", String.valueOf(definition.getCode()));
        return Result.success(result);
    }

    @GetMapping("/instances")
    public Result<List<Map<String, Object>>> instances(
                                                       @RequestAttribute(Constants.SESSION_USER) User user,
                                                       @RequestParam("projectCode") long projectCode) {
        projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WorkflowDefinition workflow : workflowDefinitionDao.queryAllDefinitionList(projectCode)) {
            if (!workflow.getName().startsWith(WORKFLOW_PREFIX)) {
                continue;
            }
            TaskDefinition task = getTask(workflow.getCode());
            if (task == null || !"DATA_SYNC".equals(task.getTaskType())) {
                continue;
            }
            List<WorkflowInstanceSummaryDto> runs =
                    workflowInstanceDao.queryByWorkflowDefinitionCode(workflow.getCode(), 50);
            for (WorkflowInstanceSummaryDto run : runs) {
                List<TaskInstance> attempts = taskInstanceDao.queryByWorkflowInstanceId(run.getId());
                if (attempts.isEmpty()) {
                    Map<String, Object> pending = new LinkedHashMap<>();
                    pending.put("id", null);
                    pending.put("workflowInstanceId", run.getId());
                    pending.put("workflowCode", String.valueOf(workflow.getCode()));
                    pending.put("taskName", task.getName());
                    pending.put("status", run.getState().isFinalState()
                            ? run.getState().name()
                            : "SUBMITTED");
                    pending.put("startTime", run.getStartTime());
                    rows.add(pending);
                } else {
                    for (TaskInstance attempt : attempts) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", String.valueOf(attempt.getId()));
                        item.put("workflowInstanceId", run.getId());
                        item.put("workflowCode", String.valueOf(workflow.getCode()));
                        item.put("taskName", attempt.getName());
                        item.put("status", attempt.getState().name());
                        item.put("startTime", attempt.getStartTime());
                        item.put("endTime", attempt.getEndTime());
                        item.put("host", attempt.getHost());
                        rows.add(item);
                    }
                }
            }
        }
        rows.sort((left, right) -> Integer.compare(
                ((Number) right.get("workflowInstanceId")).intValue(),
                ((Number) left.get("workflowInstanceId")).intValue()));
        return Result.success(rows);
    }

    @PostMapping("/instances/{id}/stop")
    public Result<Boolean> stop(
                                @RequestAttribute(Constants.SESSION_USER) User user,
                                @PathVariable("id") int instanceId,
                                @RequestParam("projectCode") long projectCode) {
        TaskInstance instance = authorizedInstance(user, projectCode, instanceId);
        executorService.controlWorkflowInstance(user, instance.getWorkflowInstanceId(), ExecuteType.STOP);
        return Result.success(true);
    }

    @GetMapping("/instances/{id}/logs")
    public Result<Map<String, Object>> logs(
                                            @RequestAttribute(Constants.SESSION_USER) User user,
                                            @PathVariable("id") int instanceId,
                                            @RequestParam("projectCode") long projectCode,
                                            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        TaskInstance instance = authorizedInstance(user, projectCode, instanceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", "");
        result.put("offset", offset);
        if (instance.getHost() == null || instance.getLogPath() == null) {
            return Result.success(result);
        }
        TaskInstanceLogPageQueryRequest request = TaskInstanceLogPageQueryRequest.builder()
                .taskInstanceId(instanceId)
                .taskInstanceLogAbsolutePath(instance.getLogPath())
                .skipLineNum(Math.max(0, offset))
                .limit(200)
                .build();
        TaskInstanceLogPageQueryResponse page = Clients.withService(ILogService.class)
                .withHost(instance.getHost())
                .pageQueryTaskInstanceLog(request);
        String content = page.getLogContent() == null ? "" : page.getLogContent();
        result.put("content", content);
        int count = content.isEmpty() ? 0 : content.split("\\r?\\n", -1).length;
        if (content.endsWith("\n")) {
            count--;
        }
        result.put("offset", offset + count);
        return Result.success(result);
    }

    private TaskDefinition authorizedTask(User user, long projectCode, long workflowCode) {
        projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
        WorkflowDefinition workflow = workflowDefinitionDao.queryByCode(workflowCode)
                .orElseThrow(() -> new IllegalArgumentException("Sync task not found"));
        if (workflow.getProjectCode() != projectCode || !workflow.getName().startsWith(WORKFLOW_PREFIX)) {
            throw new IllegalArgumentException("Sync task not found");
        }
        TaskDefinition task = getTask(workflowCode);
        if (task == null || !"DATA_SYNC".equals(task.getTaskType())) {
            throw new IllegalArgumentException("Sync task not found");
        }
        return task;
    }

    private TaskInstance authorizedInstance(User user, long projectCode, int instanceId) {
        projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
        TaskInstance instance = taskInstanceDao.queryById(instanceId);
        if (instance == null || instance.getProjectCode() != projectCode
                || !"DATA_SYNC".equals(instance.getTaskType())) {
            throw new IllegalArgumentException("Sync instance not found");
        }
        io.yak.ops.dao.entity.WorkflowInstance workflow =
                workflowInstanceDao.queryById(instance.getWorkflowInstanceId());
        if (workflow == null) {
            throw new IllegalArgumentException("Sync workflow not found");
        }
        authorizedTask(user, projectCode, workflow.getWorkflowDefinitionCode());
        return instance;
    }

    private TaskDefinition getTask(long workflowCode) {
        List<WorkflowTaskRelation> relations = workflowTaskRelationDao.queryByWorkflowDefinitionCode(workflowCode);
        if (relations.size() != 1) {
            return null;
        }
        return taskDefinitionDao.queryByCode(relations.get(0).getPostTaskCode());
    }

    private static Map<String, Object> endpoint(String type, int datasourceId, String database, String table) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        Map<String, String> tablePath = new LinkedHashMap<>();
        tablePath.put("database", database);
        tablePath.put("table", table);
        endpoint.put("type", type);
        endpoint.put("datasourceId", String.valueOf(datasourceId));
        endpoint.put("tables", Collections.singletonList(tablePath));
        return endpoint;
    }

    private static void requireText(String text, String label) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    @Data
    public static class CreateRequest {

        private long projectCode;
        private String name;
        private int sourceDataSourceId;
        private String sourceDatabase;
        private String sourceTable;
        private int targetDataSourceId;
        private String targetDatabase;
        private String targetTable;
    }
}
