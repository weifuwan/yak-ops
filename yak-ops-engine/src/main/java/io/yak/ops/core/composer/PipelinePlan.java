package io.yak.ops.core.composer;

import java.util.List;
import java.util.Objects;

/** Stable, non-secret resource claims and revision for a planned pipeline. */
public final class PipelinePlan {
    private final List<ResourceClaim> resources;
    private final String resourceRevision;

    public PipelinePlan(List<ResourceClaim> resources, String resourceRevision) {
        this.resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        this.resourceRevision = Objects.requireNonNull(resourceRevision, "resourceRevision");
        if (resourceRevision.isBlank()) throw new IllegalArgumentException("Resource revision is required");
    }

    public List<ResourceClaim> resources() {
        return resources;
    }

    public String resourceRevision() {
        return resourceRevision;
    }

    public static final class ResourceClaim {
        private final String type;
        private final String key;

        public ResourceClaim(String type, String key) {
            this.type = Objects.requireNonNull(type, "type");
            this.key = Objects.requireNonNull(key, "key");
            if (type.isBlank() || key.isBlank()) throw new IllegalArgumentException("Resource claim must not be blank");
        }

        public String type() {
            return type;
        }

        public String key() {
            return key;
        }
    }
}
