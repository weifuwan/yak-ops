package org.apache.flink.cdc.debezium.table;

import org.apache.flink.table.types.logical.LogicalType;

import java.io.Serializable;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Factory to create {@link DeserializationRuntimeConverter} according to {@link LogicalType}. It's
 * usually used to create a user-defined {@link DeserializationRuntimeConverter} which has a higher
 * resolve order than default converter.
 */
public interface DeserializationRuntimeConverterFactory extends Serializable {

    /** A user-defined converter factory which always fallback to default converters. */
    DeserializationRuntimeConverterFactory DEFAULT =
            (logicalType, serverTimeZone) -> Optional.empty();

    /**
     * Returns an optional {@link DeserializationRuntimeConverter}. Returns {@link Optional#empty()}
     * if fallback to default converter.
     *
     * @param logicalType the Flink Table & SQL internal datatype to be converted from objects of
     *     Debezium
     * @param serverTimeZone TimeZone used to convert data with timestamp type
     */
    Optional<DeserializationRuntimeConverter> createUserDefinedConverter(
            LogicalType logicalType, ZoneId serverTimeZone);
}
