package io.yak.ops.plugin.task.api.execution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Describes exclusive execution resources and the metadata revision to verify again
 * before the physical Task starts. This is not a second execution plan.
 */
public final class TaskExecutionResources {
    private final List<ExecutionResource> exclusiveResources;
    private final String resourceRevision;

    @JsonCreator
    public TaskExecutionResources(
            @JsonProperty("exclusiveResources") List<ExecutionResource> exclusiveResources,
            @JsonProperty("resourceRevision") String resourceRevision) {
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
        this.exclusiveResources = List.copyOf(resources);
        this.resourceRevision = resourceRevision;
    }

    @JsonProperty("exclusiveResources")
    public List<ExecutionResource> exclusiveResources() {
        return exclusiveResources;
    }

    @JsonProperty("resourceRevision")
    public String resourceRevision() {
        return resourceRevision;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof TaskExecutionResources other)) return false;
        return exclusiveResources.equals(other.exclusiveResources) && resourceRevision.equals(other.resourceRevision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exclusiveResources, resourceRevision);
    }
}
