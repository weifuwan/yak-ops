package io.yak.ops.server.master.engine;

import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.metrics.WorkflowInstanceMetrics;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

import lombok.NonNull;

@Component
public class WorkflowCacheRepository implements IWorkflowRepository {

    private final Map<Integer, IWorkflowExecution> workflowExecutionMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerMetrics() {
        WorkflowInstanceMetrics.registerWorkflowInstanceRunningGauge(workflowExecutionMap::size);
    }

    @Override
    public IWorkflowExecution get(final int workflowInstanceId) {
        return workflowExecutionMap.get(workflowInstanceId);
    }

    @Override
    public boolean contains(final int workflowInstanceId) {
        return workflowExecutionMap.containsKey(workflowInstanceId);
    }

    @Override
    public void remove(final int workflowInstanceId) {
        workflowExecutionMap.remove(workflowInstanceId);
    }

    @Override
    public void put(@NonNull final IWorkflowExecution workflowExecution) {
        final Integer workflowInstanceId = workflowExecution.getId();
        workflowExecutionMap.put(workflowInstanceId, workflowExecution);
    }

    @Override
    public Collection<IWorkflowExecution> getAll() {
        return ImmutableList.copyOf(workflowExecutionMap.values());
    }

}
