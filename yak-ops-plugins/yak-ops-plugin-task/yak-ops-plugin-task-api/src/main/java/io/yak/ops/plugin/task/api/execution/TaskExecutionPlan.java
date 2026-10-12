package io.yak.ops.plugin.task.api.execution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable resource admission plan produced without execution side effects.
 *
 * <p>The revision identifies the non-secret resource binding checked again before
 * physical execution. Neither connection credentials nor running handles are part of a plan.
 */
public record TaskExecutionPlan(List<ExecutionResource> exclusiveResources, String resourceRevision) {

    public TaskExecutionPlan {
        Objects.requireNonNull(exclusiveResources, "exclusiveResources");
        Objects.requireNonNull(resourceRevision, "resourceRevision");
        if (resourceRevision.isBlank() || resourceRevision.length() > 128) {
            throw new IllegalArgumentException("Invalid execution resource revision");
        }
        List<ExecutionResource> resources = new ArrayList<>(exclusiveResources);
        resources.sort(Comparator.comparing(ExecutionResource::type).thenComparing(ExecutionResource::key));
        if (new HashSet<>(resources).size() != resources.size()) {
            throw new IllegalArgumentException("Duplicate exclusive execution resource");
        }
        exclusiveResources = List.copyOf(resources);
    }
}
