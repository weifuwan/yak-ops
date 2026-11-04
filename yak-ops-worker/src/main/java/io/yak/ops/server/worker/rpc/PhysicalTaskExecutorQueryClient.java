package io.yak.ops.server.worker.rpc;

import io.yak.ops.extract.worker.ITaskExecutorQueryClient;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryRequest;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryResponse;
import io.yak.ops.server.worker.executor.PhysicalTaskEngineDelegator;
import io.yak.ops.task.executor.dto.TaskExecutorDTO;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PhysicalTaskExecutorQueryClient implements ITaskExecutorQueryClient {

    private final PhysicalTaskEngineDelegator physicalTaskEngineDelegator;

    public PhysicalTaskExecutorQueryClient(PhysicalTaskEngineDelegator physicalTaskEngineDelegator) {
        this.physicalTaskEngineDelegator = physicalTaskEngineDelegator;
    }

    @Override
    public TaskExecutorQueryResponse queryTaskInstances(TaskExecutorQueryRequest request) {
        try {
            List<TaskExecutorDTO> tasks = physicalTaskEngineDelegator.queryTaskExecutors();
            return TaskExecutorQueryResponse.success(tasks);
        } catch (Exception ex) {
            log.error("Query running task instances failed", ex);
            return TaskExecutorQueryResponse.fail(
                    "Query running task instances failed: " + ExceptionUtils.getMessage(ex));
        }
    }

}
