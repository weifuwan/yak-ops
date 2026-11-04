package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.worker.ITaskExecutorQueryClient;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryRequest;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryResponse;
import io.yak.ops.server.master.engine.executor.LogicTaskEngineDelegator;
import io.yak.ops.task.executor.dto.TaskExecutorDTO;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LogicTaskExecutorQueryClient implements ITaskExecutorQueryClient {

    private final LogicTaskEngineDelegator logicTaskEngineDelegator;

    public LogicTaskExecutorQueryClient(LogicTaskEngineDelegator logicTaskEngineDelegator) {
        this.logicTaskEngineDelegator = logicTaskEngineDelegator;
    }

    @Override
    public TaskExecutorQueryResponse queryTaskInstances(TaskExecutorQueryRequest request) {
        try {
            List<TaskExecutorDTO> tasks = logicTaskEngineDelegator.queryTaskExecutors();
            return TaskExecutorQueryResponse.success(tasks);
        } catch (Exception ex) {
            log.error("Query running task instances failed", ex);
            return TaskExecutorQueryResponse.fail(
                    "Query running task instances failed: " + ExceptionUtils.getMessage(ex));
        }
    }

}
