package io.yak.ops.server.worker.rpc;

import io.yak.ops.extract.worker.IStreamingTaskInstanceOperator;
import io.yak.ops.extract.worker.transportor.TaskInstanceTriggerSavepointRequest;
import io.yak.ops.extract.worker.transportor.TaskInstanceTriggerSavepointResponse;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.stream.StreamTask;
import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.worker.executor.PhysicalTaskExecutor;
import io.yak.ops.server.worker.executor.PhysicalTaskExecutorRepository;
import io.yak.ops.task.executor.ITaskExecutor;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StreamingTaskInstanceOperatorImpl implements IStreamingTaskInstanceOperator {

    @Autowired
    private PhysicalTaskExecutorRepository physicalTaskExecutorRepository;

    @Override
    public TaskInstanceTriggerSavepointResponse triggerSavepoint(
            TaskInstanceTriggerSavepointRequest request) {
        return execute(request, false);
    }

    @Override
    public TaskInstanceTriggerSavepointResponse stopWithSavepoint(
            TaskInstanceTriggerSavepointRequest request) {
        return execute(request, true);
    }

    private TaskInstanceTriggerSavepointResponse execute(
            TaskInstanceTriggerSavepointRequest request, boolean stop) {
        int taskInstanceId = request.getTaskInstanceId();
        log.info("Receive {} request for taskInstanceId={}",
                stop ? "stopWithSavepoint" : "triggerSavepoint", taskInstanceId);
        try {
            LogUtils.setTaskInstanceIdMDC(taskInstanceId);
            Optional<ITaskExecutor> taskExecutorOptional = physicalTaskExecutorRepository.get(taskInstanceId);
            if (!taskExecutorOptional.isPresent()) {
                return TaskInstanceTriggerSavepointResponse.fail(
                        "Cannot find running task executor for taskInstanceId=" + taskInstanceId);
            }
            PhysicalTaskExecutor taskExecutor = (PhysicalTaskExecutor) taskExecutorOptional.get();
            AbstractTask task = taskExecutor.getPhysicalTask();
            if (!(task instanceof StreamTask)) {
                return TaskInstanceTriggerSavepointResponse.fail("The taskInstance is not a StreamTask");
            }
            try {
                String path = stop
                        ? ((StreamTask) task).stopWithSavepoint()
                        : ((StreamTask) task).savePoint();
                if (path == null || path.trim().isEmpty()) {
                    return TaskInstanceTriggerSavepointResponse.fail("Flink did not return a savepoint path");
                }
                return TaskInstanceTriggerSavepointResponse.success(path);
            } catch (Exception exception) {
                log.error("StreamTask {} {} failed", taskInstanceId,
                        stop ? "stopWithSavepoint" : "savePoint", exception);
                return TaskInstanceTriggerSavepointResponse.fail(exception.getMessage());
            }
        } finally {
            LogUtils.removeTaskInstanceIdMDC();
        }
    }
}
