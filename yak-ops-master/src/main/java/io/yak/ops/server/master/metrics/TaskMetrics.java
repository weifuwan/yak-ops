package io.yak.ops.server.master.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableSet;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TaskMetrics {

    private final Map<String, Counter> taskInstanceCounters = new HashMap<>();

    private final Set<String> taskInstanceStates = ImmutableSet.of(
            "submit", "timeout", "finish", "failover", "retry", "dispatch", "success", "kill", "fail", "stop");

    static {
        for (final String state : taskInstanceStates) {
            taskInstanceCounters.put(
                    state,
                    Counter.builder("ds.task.instance.count")
                            .tags("state", state)
                            .description(String.format("Workflow instance %s total count", state))
                            .register(Metrics.globalRegistry));
        }

    }

    private final Counter taskDispatchCounter =
            Counter.builder("ds.task.dispatch.count")
                    .description("Task dispatch count")
                    .register(Metrics.globalRegistry);

    private final Counter taskDispatchFailCounter =
            Counter.builder("ds.task.dispatch.failure.count")
                    .description("Task dispatch failures count, retried ones included")
                    .register(Metrics.globalRegistry);

    private final Counter taskDispatchErrorCounter =
            Counter.builder("ds.task.dispatch.error.count")
                    .description("Number of errors during task dispatch")
                    .register(Metrics.globalRegistry);

    public synchronized void registerTaskPrepared(Supplier<Number> consumer) {
        Gauge.builder("ds.task.prepared", consumer)
                .description("Task prepared count")
                .register(Metrics.globalRegistry);
    }

    public void incTaskDispatchFailed(int failedCount) {
        taskDispatchFailCounter.increment(failedCount);
    }

    public void incTaskDispatchError() {
        taskDispatchErrorCounter.increment();
    }

    public void incTaskDispatch() {
        taskDispatchCounter.increment();
    }

    public void incTaskInstanceByState(final String state) {
        if (taskInstanceCounters.get(state) == null) {
            return;
        }
        taskInstanceCounters.get(state).increment();
    }

}
