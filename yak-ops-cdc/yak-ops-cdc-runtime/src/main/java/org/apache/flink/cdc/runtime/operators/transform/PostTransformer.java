package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.cdc.common.schema.Selectors;
import org.apache.flink.cdc.common.source.SupportedMetadataColumn;
import org.apache.flink.cdc.runtime.operators.transform.converter.PostTransformConverter;

import javax.annotation.Nullable;

import java.util.Optional;

/** Post-Transformation rule used by {@link PostTransformOperator}. */
public class PostTransformer {
    private final Selectors selectors;

    private final @Nullable TransformProjection projection;
    private final @Nullable TransformFilter filter;
    private final @Nullable PostTransformConverter postTransformConverter;
    private final SupportedMetadataColumn[] supportedMetadataColumns;

    public PostTransformer(
            Selectors selectors,
            @Nullable TransformProjection projection,
            @Nullable TransformFilter filter,
            @Nullable PostTransformConverter postTransformConverter,
            SupportedMetadataColumn[] supportedMetadataColumns) {
        this.selectors = selectors;
        this.projection = projection;
        this.filter = filter;
        this.postTransformConverter = postTransformConverter;
        this.supportedMetadataColumns = supportedMetadataColumns;
    }

    public Selectors getSelectors() {
        return selectors;
    }

    public Optional<TransformProjection> getProjection() {
        return Optional.ofNullable(projection);
    }

    public Optional<TransformFilter> getFilter() {
        return Optional.ofNullable(filter);
    }

    public Optional<PostTransformConverter> getPostTransformConverter() {
        return Optional.ofNullable(postTransformConverter);
    }

    public SupportedMetadataColumn[] getSupportedMetadataColumns() {
        return supportedMetadataColumns;
    }
}
