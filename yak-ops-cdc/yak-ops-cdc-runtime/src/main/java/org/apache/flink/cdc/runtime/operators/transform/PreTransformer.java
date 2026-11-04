package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.cdc.common.schema.Selectors;

import javax.annotation.Nullable;

import java.util.Optional;

/** Pre-Transformation rule used by {@link PreTransformOperator}. */
public class PreTransformer {
    private final Selectors selectors;

    private final Optional<TransformProjection> projection;
    private final Optional<TransformFilter> filter;

    public PreTransformer(
            Selectors selectors,
            @Nullable TransformProjection projection,
            @Nullable TransformFilter filter) {
        this.selectors = selectors;
        this.projection = projection != null ? Optional.of(projection) : Optional.empty();
        this.filter = filter != null ? Optional.of(filter) : Optional.empty();
    }

    public Selectors getSelectors() {
        return selectors;
    }

    public Optional<TransformProjection> getProjection() {
        return projection;
    }

    public Optional<TransformFilter> getFilter() {
        return filter;
    }
}
