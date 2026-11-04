package org.apache.flink.cdc.runtime.operators.transform.converter;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.utils.StringUtils;

import java.util.Optional;

/** The {@link PostTransformConverter} utils. */
@Experimental
public class PostTransformConverters {
    public static final String SOFT_DELETE_CONVERTER = "SOFT_DELETE";

    /** Get the {@link PostTransformConverter} by given identifier. */
    public static Optional<PostTransformConverter> of(String identifier) {
        if (StringUtils.isNullOrWhitespaceOnly(identifier)) {
            return Optional.empty();
        }

        switch (identifier) {
            case SOFT_DELETE_CONVERTER:
                return Optional.of(new SoftDeleteConverter());
            default:
                throw new IllegalArgumentException(
                        String.format("Failed to find the converter %s.", identifier));
        }
    }
}
