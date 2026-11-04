package io.yak.ops.server.master.metrics;

import io.yak.ops.common.enums.WorkflowExecutionStatus;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableSet;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class WorkflowInstanceMetrics {

    private final Set<String> workflowInstanceStates = ImmutableSet.of(
            WorkflowExecutionStatus.SUBMITTED_SUCCESS.name(),
            WorkflowExecutionStatus.RUNNING_EXECUTION.name(),
            WorkflowExecutionStatus.READY_PAUSE.name(),
            WorkflowExecutionStatus.PAUSE.name(),
            WorkflowExecutionStatus.READY_STOP.name(),
            WorkflowExecutionStatus.FAILOVER.name(),
            WorkflowExecutionStatus.SUCCESS.name(),
            WorkflowExecutionStatus.FAILURE.name(),
            WorkflowExecutionStatus.STOP.name(),
            WorkflowExecutionStatus.SERIAL_WAIT.name());

    static {
        for (final String state : workflowInstanceStates) {
            Counter.builder("ds.workflow.instance.count")
                    .tags("state", state, "workflow.definition.code", "dummy")
                    .description(String.format("workflow instance total count by state and definition code"))
                    .register(Metrics.globalRegistry);
        }

    }

    private final Timer commandQueryTimer =
            Timer.builder("ds.workflow.command.query.duration")
                    .description("Command query duration")
                    .register(Metrics.globalRegistry);

    private final Timer workflowInstanceGenerateTimer =
            Timer.builder("ds.workflow.instance.generate.duration")
                    .description("workflow instance generated duration")
                    .register(Metrics.globalRegistry);

    public void recordCommandQueryTime(long milliseconds) {
        commandQueryTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }

    public void recordWorkflowInstanceGenerateTime(long milliseconds) {
        workflowInstanceGenerateTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }

    public synchronized void registerWorkflowInstanceRunningGauge(Supplier<Number> function) {
        Gauge.builder("ds.workflow.instance.running", function)
                .description("The current running workflow instance count")
                .register(Metrics.globalRegistry);
    }

    public synchronized void registerWorkflowInstanceResubmitGauge(Supplier<Number> function) {
        Gauge.builder("ds.workflow.instance.resubmit", function)
                .description("The current workflow instance need to resubmit count")
                .register(Metrics.globalRegistry);
    }

    public void incWorkflowInstanceByStateAndWorkflowDefinitionCode(final WorkflowExecutionStatus state,
                                                                    final String workflowDefinitionCode) {
        // When tags need to be determined from local context,
        // you have no choice but to construct or lookup the Meter inside your method body.
        // The lookup cost is just a single hash lookup, so it is acceptable for most use cases.
        Metrics.globalRegistry.counter(
                "ds.workflow.instance.count",
                "state", state.name(),
                "workflow.definition.code", workflowDefinitionCode)
                .increment();
    }

    public void recordWorkflowInstanceSubmit(final Long workflowDefinitionCode) {
        incWorkflowInstanceByStateAndWorkflowDefinitionCode(
                WorkflowExecutionStatus.SUBMITTED_SUCCESS,
                String.valueOf(workflowDefinitionCode));
    }

    public void recordWorkflowInstanceFailover(final Long workflowDefinitionCode) {
        incWorkflowInstanceByStateAndWorkflowDefinitionCode(
                WorkflowExecutionStatus.FAILOVER,
                String.valueOf(workflowDefinitionCode));
    }

    public void recordWorkflowInstanceFinish(final WorkflowExecutionStatus workflowExecutionStatus,
                                             final Long workflowDefinitionCode) {
        if (workflowExecutionStatus == null || !workflowExecutionStatus.isFinalState()) {
            return;
        }
        incWorkflowInstanceByStateAndWorkflowDefinitionCode(workflowExecutionStatus,
                String.valueOf(workflowDefinitionCode));
    }

}
