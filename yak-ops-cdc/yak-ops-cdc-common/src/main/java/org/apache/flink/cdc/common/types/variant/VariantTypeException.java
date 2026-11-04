package org.apache.flink.cdc.common.types.variant;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Copy from <a
 * href="https://github.com/apache/flink/blob/release-2.2.0/flink-core/src/main/java/org/apache/flink/types/variant/VariantTypeException.java">VariantTypeException.java</a>
 *
 * <p>Exception thrown when a variant value is not of the expected type.
 */
@PublicEvolving
public class VariantTypeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public VariantTypeException(String message) {
        super(message);
    }
}
