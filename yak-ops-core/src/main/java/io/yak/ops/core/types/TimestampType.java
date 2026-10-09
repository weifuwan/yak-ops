package io.yak.ops.core.types;

/** Timestamp without time zone and with explicit fractional-second precision. */
public final class TimestampType extends LogicalType {

    private final int precision;

    public TimestampType(int precision) {
        this(true, precision);
    }

    public TimestampType(boolean nullable, int precision) {
        super(nullable, LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE);
        if (precision < 0 || precision > 9) {
            throw new IllegalArgumentException("TIMESTAMP precision must be between 0 and 9");
        }
        this.precision = precision;
    }

    public int precision() {
        return precision;
    }

    @Override
    public TimestampType copy(boolean nullable) {
        return new TimestampType(nullable, precision);
    }

    @Override
    public String asSerializableString() {
        return withNullability("TIMESTAMP(" + precision + ")");
    }

    @Override
    public Class<?> getDefaultConversion() {
        return java.time.LocalDateTime.class;
    }

    @Override
    protected Object parameters() {
        return precision;
    }
}
