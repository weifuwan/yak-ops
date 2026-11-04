package io.yak.ops.api.service.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.yak.ops.common.constants.CommandKeyConstants.CMD_PARAM_RECOVER_WORKFLOW_ID_STRING;
import static io.yak.ops.common.constants.CommandKeyConstants.CMD_PARAM_START_NODES;
import static io.yak.ops.common.constants.CommandKeyConstants.CMD_PARAM_SUB_WORKFLOW_DEFINITION_CODE;

import io.yak.ops.api.dto.workflow.WorkflowBackFillRequest;
import io.yak.ops.api.dto.workflow.WorkflowTriggerRequest;
import io.yak.ops.api.dto.workflowInstance.WorkflowExecuteResponse;
import io.yak.ops.api.enums.ExecuteType;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.executor.workflow.ExecutorClient;
import io.yak.ops.api.service.ExecutorService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.validator.workflow.BackfillWorkflowDTO;
import io.yak.ops.api.validator.workflow.BackfillWorkflowDTOValidator;
import io.yak.ops.api.validator.workflow.BackfillWorkflowRequestTransformer;
import io.yak.ops.api.validator.workflow.TriggerWorkflowDTO;
import io.yak.ops.api.validator.workflow.TriggerWorkflowDTOValidator;
import io.yak.ops.api.validator.workflow.TriggerWorkflowRequestTransformer;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskGroupQueue;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.mapper.TaskDefinitionLogMapper;
import io.yak.ops.dao.mapper.TaskGroupQueueMapper;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;
import io.yak.ops.plugin.task.api.utils.TaskTypeUtils;
import io.yak.ops.service.command.CommandService;
import io.yak.ops.service.process.ProcessService;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExecutorServiceImpl extends BaseServiceImpl implements ExecutorService {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Lazy()
    @Autowired
    private ProcessService processService;

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private CommandService commandService;

    @Autowired
    private TaskDefinitionLogMapper taskDefinitionLogMapper;

    @Autowired
    private TaskDefinitionDao taskDefinitionDao;

    @Autowired
    private WorkflowTaskRelationDao workflowTaskRelationDao;

    @Autowired
    private TaskGroupQueueMapper taskGroupQueueMapper;

    @Autowired
    private TriggerWorkflowRequestTransformer triggerWorkflowRequestTransformer;

    @Autowired
    private TriggerWorkflowDTOValidator triggerWorkflowDTOValidator;

    @Autowired
    private BackfillWorkflowRequestTransformer backfillWorkflowRequestTransformer;

    @Autowired
    private BackfillWorkflowDTOValidator backfillWorkflowDTOValidator;

    @Autowired
    private ExecutorClient executorClient;

    @Override
    @Transactional
    public Integer triggerWorkflowDefinition(final WorkflowTriggerRequest triggerRequest) {
        projectService.checkHasProjectWritePermissionThrowException(
                triggerRequest.getLoginUser(), triggerRequest.getProjectCode());
        final TriggerWorkflowDTO triggerWorkflowDTO = triggerWorkflowRequestTransformer.transform(triggerRequest);
        // verify the workflow definition belongs to the URL's project
        if (triggerWorkflowDTO.getWorkflowDefinition() == null
                || triggerWorkflowDTO.getWorkflowDefinition().getProjectCode() != triggerRequest.getProjectCode()) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_EXIST,
                    String.valueOf(triggerRequest.getWorkflowDefinitionCode()));
        }
        // todo: use validator chain
        triggerWorkflowDTOValidator.validate(triggerWorkflowDTO);
        return executorClient.triggerWorkflowDefinition().execute(triggerWorkflowDTO);
    }

    @Override
    @Transactional
    public List<Integer> backfillWorkflowDefinition(final WorkflowBackFillRequest workflowBackFillRequest) {
        projectService.checkHasProjectWritePermissionThrowException(
                workflowBackFillRequest.getLoginUser(), workflowBackFillRequest.getProjectCode());
        final BackfillWorkflowDTO backfillWorkflowDTO =
                backfillWorkflowRequestTransformer.transform(workflowBackFillRequest);
        // verify the workflow definition belongs to the URL's project
        if (backfillWorkflowDTO.getWorkflowDefinition() == null
                || backfillWorkflowDTO.getWorkflowDefinition().getProjectCode() != workflowBackFillRequest
                        .getProjectCode()) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_EXIST,
                    String.valueOf(workflowBackFillRequest.getWorkflowDefinitionCode()));
        }
        backfillWorkflowDTOValidator.validate(backfillWorkflowDTO);
        return executorClient.backfillWorkflowDefinition().execute(backfillWorkflowDTO);
    }

    /**
     * check whether the workflow definition can be executed
     *
     * @param projectCode       project code
     * @param workflowDefinition workflow definition
     */
    @Override
    public void checkWorkflowDefinitionValid(long projectCode, WorkflowDefinition workflowDefinition,
                                             long workflowDefinitionCode, Integer version) {
        // check workflow definition exists
        if (projectCode != workflowDefinition.getProjectCode()) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_EXIST, workflowDefinition.getCode());
        }
        // check workflow definition online
        if (workflowDefinition.getReleaseState() != ReleaseState.ONLINE) {
            throw new ServiceException(Status.WORKFLOW_DEFINITION_NOT_RELEASE, workflowDefinition.getCode(),
                    workflowDefinition.getVersion());
        }
        // check sub workflow definition online
        if (!checkSubWorkflowDefinitionValid(workflowDefinition)) {
            throw new ServiceException(Status.SUB_WORKFLOW_DEFINITION_NOT_RELEASE);
        }
    }

    /**
     * check whether the current workflow has sub workflows and validate all sub workflows
     *
     * @param workflowDefinition
     * @return check result
     */
    @Override
    public boolean checkSubWorkflowDefinitionValid(WorkflowDefinition workflowDefinition) {
        // query all sub workflows under the current workflow
        List<WorkflowTaskRelation> workflowTaskRelations =
                workflowTaskRelationDao.queryDownstreamByWorkflowDefinitionCode(workflowDefinition.getCode());
        if (workflowTaskRelations.isEmpty()) {
            return true;
        }
        Set<Long> relationCodes =
                workflowTaskRelations.stream().map(WorkflowTaskRelation::getPostTaskCode).collect(Collectors.toSet());
        List<TaskDefinition> taskDefinitions = taskDefinitionDao.queryByCodes(relationCodes);

        // find out the workflow definition code
        Set<Long> workflowDefinitionCodeSet = new HashSet<>();
        taskDefinitions.stream()
                .filter(task -> TaskTypeUtils.isSubWorkflowTask(task.getTaskType())).forEach(
                        taskDefinition -> workflowDefinitionCodeSet.add(Long.valueOf(
                                JSONUtils.getNodeString(taskDefinition.getTaskParams(),
                                        CMD_PARAM_SUB_WORKFLOW_DEFINITION_CODE))));
        if (workflowDefinitionCodeSet.isEmpty()) {
            return true;
        }

        // check sub releaseState
        List<WorkflowDefinition> workflowDefinitions = workflowDefinitionDao.queryByCodes(workflowDefinitionCodeSet);
        return workflowDefinitions.stream()
                .filter(definition -> definition.getReleaseState().equals(ReleaseState.OFFLINE))
                .collect(Collectors.toSet())
                .isEmpty();
    }

    @Override
    public void controlWorkflowInstance(User loginUser, Integer workflowInstanceId, ExecuteType executeType) {
        checkNotNull(workflowInstanceId, "workflowInstanceId cannot be null");
        checkNotNull(executeType, "executeType cannot be null");

        WorkflowInstance workflowInstance = workflowInstanceDao
                .queryOptionalById(workflowInstanceId)
                .orElseThrow(() -> new ServiceException(Status.WORKFLOW_INSTANCE_NOT_EXIST, workflowInstanceId));

        projectService.checkHasProjectWritePermissionThrowException(loginUser, workflowInstance.getProjectCode());

        switch (executeType) {
            case REPEAT_RUNNING:
                executorClient
                        .repeatRunningWorkflowInstance()
                        .onWorkflowInstance(workflowInstance)
                        .byUser(loginUser)
                        .execute();
                return;
            case START_FAILURE_TASK_PROCESS:
                executorClient.recoverFailureTaskInstance()
                        .onWorkflowInstance(workflowInstance)
                        .byUser(loginUser)
                        .execute();
                return;
            case RECOVER_SUSPENDED_PROCESS:
                executorClient.recoverSuspendedWorkflowInstanceOperation()
                        .onWorkflowInstance(workflowInstance)
                        .byUser(loginUser)
                        .execute();
                return;
            case PAUSE:
                executorClient.pauseWorkflowInstance()
                        .onWorkflowInstance(workflowInstance)
                        .byUser(loginUser)
                        .execute();
                return;
            case STOP:
                executorClient.stopWorkflowInstance()
                        .onWorkflowInstance(workflowInstance)
                        .byUser(loginUser)
                        .execute();
                return;
            default:
                throw new ServiceException("Unsupported executeType: " + executeType);
        }
    }

    /**
     * do action to execute task in workflow instance
     *
     * @param loginUser         login user
     * @param projectCode       project code
     * @param workflowInstanceId workflow instance id
     * @param startNodeList     start node list
     * @param taskDependType    task depend type
     * @return execute result code
     */
    @Override
    public WorkflowExecuteResponse executeTask(User loginUser,
                                               long projectCode,
                                               Integer workflowInstanceId,
                                               String startNodeList,
                                               TaskDependType taskDependType) {

        WorkflowExecuteResponse response = new WorkflowExecuteResponse();

        projectService.checkHasProjectWritePermissionThrowException(loginUser, projectCode);

        WorkflowInstance workflowInstance = processService.findWorkflowInstanceDetailById(workflowInstanceId)
                .orElseThrow(() -> new ServiceException(Status.WORKFLOW_INSTANCE_NOT_EXIST, workflowInstanceId));

        if (!workflowInstance.getState().isFinalState()) {
            log.error("Can not execute task for workflow instance which is not finished, workflowInstanceId:{}.",
                    workflowInstanceId);
            putMsg(response, Status.WORKFLOW_INSTANCE_IS_NOT_FINISHED);
            return response;
        }

        WorkflowDefinition workflowDefinition =
                processService.findWorkflowDefinition(workflowInstance.getWorkflowDefinitionCode(),
                        workflowInstance.getWorkflowDefinitionVersion());
        workflowDefinition.setReleaseState(ReleaseState.ONLINE);
        this.checkWorkflowDefinitionValid(projectCode, workflowDefinition, workflowInstance.getWorkflowDefinitionCode(),
                workflowInstance.getWorkflowDefinitionVersion());

        // get the startParams user specified at the first starting while repeat running is needed

        long startNodeListLong;
        try {
            startNodeListLong = Long.parseLong(startNodeList);
        } catch (NumberFormatException e) {
            log.error("startNodeList is not a number");
            putMsg(response, Status.REQUEST_PARAMS_NOT_VALID_ERROR, startNodeList);
            return response;
        }

        if (taskDefinitionLogMapper.queryMaxVersionForDefinition(startNodeListLong) == null) {
            putMsg(response, Status.EXECUTE_NOT_DEFINE_TASK);
            return response;
        }

        // To add startParams only when repeat running is needed
        Map<String, Object> cmdParam = new HashMap<>();
        cmdParam.put(CMD_PARAM_RECOVER_WORKFLOW_ID_STRING, workflowInstanceId);
        // Add StartNodeList
        cmdParam.put(CMD_PARAM_START_NODES, startNodeList);

        Command command = new Command();
        command.setCommandType(CommandType.EXECUTE_TASK);
        command.setWorkflowDefinitionCode(workflowDefinition.getCode());
        command.setCommandParam(JSONUtils.toJsonString(cmdParam));
        command.setExecutorId(loginUser.getId());
        command.setWorkflowDefinitionVersion(workflowDefinition.getVersion());
        command.setWorkflowInstanceId(workflowInstanceId);

        // Add taskDependType
        command.setTaskDependType(taskDependType);

        if (!commandService.verifyIsNeedCreateCommand(command)) {
            log.warn(
                    "workflow instance is executing the command, workflowDefinitionCode:{}, workflowDefinitionVersion:{}, workflowInstanceId:{}.",
                    workflowDefinition.getCode(), workflowDefinition.getVersion(), workflowInstanceId);
            putMsg(response, Status.WORKFLOW_INSTANCE_EXECUTING_COMMAND,
                    String.valueOf(workflowDefinition.getCode()));
            return response;
        }

        log.info("Creating command, commandInfo:{}.", command);
        int create = commandService.createCommand(command);

        if (create > 0) {
            log.info("Create {} command complete, workflowDefinitionCode:{}, workflowDefinitionVersion:{}.",
                    command.getCommandType().getDescp(), command.getWorkflowDefinitionCode(),
                    workflowDefinition.getVersion());
            putMsg(response, Status.SUCCESS);
        } else {
            log.error(
                    "Execute workflow instance failed because create {} command error, workflowDefinitionCode:{}, workflowDefinitionVersion:{}， workflowInstanceId:{}.",
                    command.getCommandType().getDescp(), command.getWorkflowDefinitionCode(),
                    workflowDefinition.getVersion(),
                    workflowInstanceId);
            putMsg(response, Status.EXECUTE_WORKFLOW_INSTANCE_ERROR);
        }

        return response;
    }

    @Override
    public void forceStartTaskInstance(User loginUser, int queueId) {
        TaskGroupQueue taskGroupQueue = taskGroupQueueMapper.selectById(queueId);
        // check workflow instance exist
        WorkflowInstance workflowInstance =
                workflowInstanceDao.queryOptionalById(taskGroupQueue.getWorkflowInstanceId())
                        .orElseThrow(
                                () -> new ServiceException(Status.WORKFLOW_INSTANCE_NOT_EXIST,
                                        taskGroupQueue.getWorkflowInstanceId()));
        projectService.checkHasProjectWritePermissionThrowException(loginUser, workflowInstance.getProjectCode());

        if (taskGroupQueue.getInQueue() == Flag.NO.getCode()) {
            throw new ServiceException(Status.TASK_GROUP_QUEUE_ALREADY_START);
        }
        taskGroupQueue.setForceStart(Flag.YES.getCode());
        taskGroupQueue.setUpdateTime(new Date());
        taskGroupQueueMapper.updateById(taskGroupQueue);
    }

    @Override
    public void execStreamTaskInstance(User loginUser,
                                       long projectCode,
                                       long taskDefinitionCode,
                                       int taskDefinitionVersion,
                                       int warningGroupId,
                                       String workerGroup,
                                       String tenantCode,
                                       Long environmentCode,
                                       Map<String, String> startParams,
                                       int dryRun) {
        throw new ServiceException("Not supported");
    }
}
