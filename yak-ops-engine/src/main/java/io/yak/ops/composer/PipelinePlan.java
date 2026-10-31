package io.yak.ops.composer;

import io.yak.ops.composer.definition.TableBinding;
import java.util.List;
import java.util.Objects;

/** Stable, non-secret resource claims and revision for a planned pipeline. */
public final class PipelinePlan {
    private final List<ResourceClaim> resources;
    private final String resourceRevision;
    private final List<TableBinding> tables;

    public PipelinePlan(List<ResourceClaim> resources, String resourceRevision) {
        this(resources, resourceRevision, List.of());
    }

    public PipelinePlan(List<ResourceClaim> resources, String resourceRevision, List<TableBinding> tables) {
        this.resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        this.resourceRevision = Objects.requireNonNull(resourceRevision, "resourceRevision");
        this.tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        if (resourceRevision.isBlank()) throw new IllegalArgumentException("Resource revision is required");
    }

    public List<ResourceClaim> resources() {
        return resources;
    }

    public String resourceRevision() {
        return resourceRevision;
    }

    /** Non-secret authorized metadata pinned by the resource revision. */
    public List<TableBinding> tables() {
        return tables;
    }

    public PipelinePlan withTables(List<TableBinding> bindings) {
        return new PipelinePlan(resources, resourceRevision, bindings);
    }

    /** Compares the admission contract independently of preparation metadata. */
    public boolean sameAdmission(PipelinePlan other) {
        return other != null && resourceRevision.equals(other.resourceRevision) && resources.equals(other.resources);
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

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof ResourceClaim other)) return false;
            return type.equals(other.type) && key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, key);
        }
    }
}
