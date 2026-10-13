package io.yak.ops.task.execution.impl;

import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.task.execution.TaskExecutionService;
import io.yak.ops.task.execution.dispatch.EmbeddedTaskDispatcher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** Sends committed submissions to the embedded dispatcher after business admission. */
@Service
public class TaskExecutionServiceImpl implements TaskExecutionService {

    @Resource
    private SubmissionService submissions;

    @Resource
    private EmbeddedTaskDispatcher dispatcher;

    @Override
    public SubmissionResult submitManual(String definitionId, String requestId, String operatorId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        SubmissionResult result = submissions.submitManual(definitionId, requestId, operatorId);
        if (!result.replayed()) {
            dispatcher.dispatch(workspaceId, result.instanceId());
        }
        return result;
    }

    @Override
    public TaskInstanceStatus cancel(String workspaceId, String instanceId) {
        return dispatcher.cancel(workspaceId, instanceId);
    }
}
