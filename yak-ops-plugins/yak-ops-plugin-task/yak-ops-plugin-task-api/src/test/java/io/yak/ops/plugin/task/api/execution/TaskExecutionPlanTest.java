package io.yak.ops.plugin.task.api.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Validates immutable and deterministic exclusive-resource declarations. */
class TaskExecutionPlanTest {

    @Test
    void sortsAndCopiesExclusiveResources() {
        ExecutionResource first = new ExecutionResource("DATA_SYNC_TARGET", "a");
        ExecutionResource second = new ExecutionResource("DATA_SYNC_TARGET", "b");
        List<ExecutionResource> resources = new java.util.ArrayList<>(List.of(second, first));
        TaskExecutionPlan plan = new TaskExecutionPlan(resources, "revision-1");
        resources.clear();
        assertEquals(List.of(first, second), plan.exclusiveResources());
        assertThrows(UnsupportedOperationException.class, () -> plan.exclusiveResources().add(first));
    }

    @Test
    void rejectsDuplicateOrInvalidResources() {
        ExecutionResource resource = new ExecutionResource("DATA_SYNC_TARGET", "ds/table");
        assertThrows(IllegalArgumentException.class, () -> new TaskExecutionPlan(List.of(resource, resource), "rev"));
        assertThrows(IllegalArgumentException.class, () -> new TaskExecutionPlan(List.of(resource), ""));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionResource("DATA_SYNC_TARGET", ""));
    }
}
