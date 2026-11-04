package io.yak.ops.server.master.engine.executor.plugin.subworkflow;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IWorkflowControlClient;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowManualTriggerRequest;
import io.yak.ops.server.master.engine.executor.plugin.subworkflow.trigger.SubWorkflowManualTrigger;
import io.yak.ops.server.master.engine.workflow.trigger.WorkflowInstanceRecoverFailureTaskTrigger;
import io.yak.ops.server.master.engine.workflow.trigger.WorkflowInstanceRecoverSuspendTaskTrigger;
import io.yak.ops.server.master.exception.MasterTaskExecuteException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SubWorkflowControlClient {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private SubWorkflowManualTrigger subWorkflowManualTrigger;

    @Autowired
    private WorkflowInstanceRecoverFailureTaskTrigger workflowInstanceRecoverFailureTaskTrigger;

    @Autowired
    private WorkflowInstanceRecoverSuspendTaskTrigger workflowInstanceRecoverSuspendTaskTrigger;

    public Integer triggerSubWorkflow(final WorkflowManualTriggerRequest workflowManualTriggerRequest) {
        return subWorkflowManualTrigger.triggerWorkflow(workflowManualTriggerRequest).getWorkflowInstanceId();
    }

    public WorkflowInstanceRecoverFailureTasksResponse triggerFromFailureTasks(
                                                                               final WorkflowInstanceRecoverFailureTasksRequest recoverFailureTasksRequest) {
        return workflowInstanceRecoverFailureTaskTrigger.triggerWorkflow(recoverFailureTasksRequest);
    }

    public WorkflowInstanceRecoverSuspendTasksResponse triggerFromSuspendTasks(
                                                                               final WorkflowInstanceRecoverSuspendTasksRequest recoverSuspendTasksRequest) {
        return workflowInstanceRecoverSuspendTaskTrigger.triggerWorkflow(recoverSuspendTasksRequest);
    }

    public WorkflowInstancePauseResponse pauseWorkflowInstance(
                                                               final WorkflowInstancePauseRequest workflowInstancePauseRequest) throws MasterTaskExecuteException {
        final Integer subWorkflowInstanceId = workflowInstancePauseRequest.getWorkflowInstanceId();
        final WorkflowInstance subWorkflowInstance = workflowInstanceDao.queryById(subWorkflowInstanceId);
        if (subWorkflowInstance.getState() != WorkflowExecutionStatus.RUNNING_EXECUTION) {
            return WorkflowInstancePauseResponse.fail("SubWorkflow instance is not running, cannot pause");
        }
        try {
            return Clients
                    .withService(IWorkflowControlClient.class)
                    .withHost(subWorkflowInstance.getHost())
                    .pauseWorkflowInstance(new WorkflowInstancePauseRequest(subWorkflowInstanceId));
        } catch (Exception e) {
            throw new MasterTaskExecuteException("Pause SubWorkflow: " + subWorkflowInstance.getName() + " failed", e);
        }
    }

    public WorkflowInstanceStopResponse stopWorkflowInstance(
                                                             final WorkflowInstanceStopRequest workflowInstanceStopRequest) throws MasterTaskExecuteException {
        final Integer subWorkflowInstanceId = workflowInstanceStopRequest.getWorkflowInstanceId();
        final WorkflowInstance subWorkflowInstance = workflowInstanceDao.queryById(subWorkflowInstanceId);
        if (subWorkflowInstance.getState() != WorkflowExecutionStatus.RUNNING_EXECUTION) {
            return WorkflowInstanceStopResponse.fail("SubWorkflow instance is not running, cannot stop");
        }
        try {
            return Clients
                    .withService(IWorkflowControlClient.class)
                    .withHost(subWorkflowInstance.getHost())
                    .stopWorkflowInstance(new WorkflowInstanceStopRequest(subWorkflowInstance.getId()));
        } catch (Exception e) {
            throw new MasterTaskExecuteException("Kill SubWorkflow: " + subWorkflowInstance.getName() + " failed", e);
        }
    }
}
