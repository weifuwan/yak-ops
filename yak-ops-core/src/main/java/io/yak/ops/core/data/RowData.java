package io.yak.ops.core.data;

import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.TimeType;
import io.yak.ops.core.types.TimestampType;
import io.yak.ops.core.types.ZonedTimestampType;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * The internal field-access contract for one YakFlow row, independent of its physical storage.
 *
 * <p>Unlike JDBC ResultSet, values have one stable Java representation per resolved logical type:
 * numbers use their boxed primitive types, DECIMAL uses BigDecimal, textual data uses String,
 * binary uses byte[], and temporal values use java.time classes. JDBC-to-internal conversion
 * belongs to the Connector, not to this interface.
 *
 * <p>RowKind and table identity remain in the surrounding TableRecord, not duplicated here.
 */
public interface RowData {

    int getArity();

    boolean isNullAt(int position);

    /** Returns an internal field or null; mutable values must not expose internal storage. */
    Object getField(int position);

    default boolean getBoolean(int position) {
        return required(position, Boolean.class);
    }

    default byte getByte(int position) {
        return required(position, Byte.class);
    }

    default short getShort(int position) {
        return required(position, Short.class);
    }

    default int getInt(int position) {
        return required(position, Integer.class);
    }

    default long getLong(int position) {
        return required(position, Long.class);
    }

    default float getFloat(int position) {
        return required(position, Float.class);
    }

    default double getDouble(int position) {
        return required(position, Double.class);
    }

    default String getString(int position) {
        return required(position, String.class);
    }

    default byte[] getBinary(int position) {
        return required(position, byte[].class);
    }

    default BigDecimal getDecimal(int position, int precision, int scale) {
        if (precision < 1 || precision > DecimalType.MAX_PRECISION || scale < 0 || scale > precision) {
            throw new IllegalArgumentException("Invalid decimal type precision/scale");
        }
        return required(position, BigDecimal.class);
    }

    default LocalDate getDate(int position) {
        return required(position, LocalDate.class);
    }

    default LocalTime getTime(int position, int precision) {
        validateTemporalPrecision(precision);
        return required(position, LocalTime.class);
    }

    default LocalDateTime getTimestamp(int position, int precision) {
        validateTemporalPrecision(precision);
        return required(position, LocalDateTime.class);
    }

    default OffsetDateTime getZonedTimestamp(int position, int precision) {
        validateTemporalPrecision(precision);
        return required(position, OffsetDateTime.class);
    }

    private <T> T required(int position, Class<T> valueType) {
        Object value = Objects.requireNonNull(getField(position), "Field at " + position + " is NULL");
        return valueType.cast(value);
    }

    private static void validateTemporalPrecision(int precision) {
        if (precision < 0 || precision > 9) {
            throw new IllegalArgumentException("Temporal precision must be between 0 and 9");
        }
    }

    /**
     * Creates a strictly typed accessor for a resolved logical column.
     *
     * <p>An unresolved decimal cannot be converted safely; its JDBC metadata must be resolved
     * before creating field accessors or passing records to a Sink.
     */
    static FieldGetter createFieldGetter(LogicalType type, int position) {
        Objects.requireNonNull(type, "type");
        if (position < 0 || !type.isResolved()) {
            throw new IllegalArgumentException("Expected resolved type and nonnegative field position");
        }
        FieldGetter getter =
                switch (type.getTypeRoot()) {
                    case BOOLEAN -> row -> row.getBoolean(position);
                    case TINYINT -> row -> row.getByte(position);
                    case SMALLINT -> row -> row.getShort(position);
                    case INTEGER -> row -> row.getInt(position);
                    case BIGINT -> row -> row.getLong(position);
                    case FLOAT -> row -> row.getFloat(position);
                    case DOUBLE -> row -> row.getDouble(position);
                    case DECIMAL -> {
                        DecimalType decimal = (DecimalType) type;
                        yield row -> row.getDecimal(position, decimal.precision(), decimal.scale());
                    }
                    case CHAR, VARCHAR -> row -> row.getString(position);
                    case BINARY, VARBINARY -> row -> row.getBinary(position);
                    case DATE -> row -> row.getDate(position);
                    case TIME_WITHOUT_TIME_ZONE -> {
                        int precision = ((TimeType) type).precision();
                        yield row -> row.getTime(position, precision);
                    }
                    case TIMESTAMP_WITHOUT_TIME_ZONE -> {
                        int precision = ((TimestampType) type).precision();
                        yield row -> row.getTimestamp(position, precision);
                    }
                    case TIMESTAMP_WITH_TIME_ZONE -> {
                        int precision = ((ZonedTimestampType) type).precision();
                        yield row -> row.getZonedTimestamp(position, precision);
                    }
                };
        return row -> row.isNullAt(position) ? null : getter.getFieldOrNull(row);
    }

    @FunctionalInterface
    interface FieldGetter extends Serializable {

        Object getFieldOrNull(RowData row);
    }
}
