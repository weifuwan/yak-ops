package org.apache.flink.cdc.runtime.operators.transform.converter;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.event.DataChangeEvent;

import java.io.Serializable;
import java.util.Optional;

/**
 * The PostTransformConverter applies to convert the {@link DataChangeEvent} after other part of
 * TransformRule in PostTransformOperator.
 */
@Experimental
public interface PostTransformConverter extends Serializable {

    /** Change some parts of {@link DataChangeEvent}, like op or meta. */
    Optional<DataChangeEvent> convert(DataChangeEvent dataChangeEvent);
}
