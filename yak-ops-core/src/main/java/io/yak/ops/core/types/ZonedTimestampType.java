package io.yak.ops.core.types;

/** Timestamp retaining an explicit offset/zone, not a locally interpreted timestamp. */
public final class ZonedTimestampType extends LogicalType {

    private final int precision;

    public ZonedTimestampType(int precision) {
        this(true, precision);
    }

    public ZonedTimestampType(boolean nullable, int precision) {
        super(nullable, LogicalTypeRoot.TIMESTAMP_WITH_TIME_ZONE);
        if (precision < 0 || precision > 9) {
            throw new IllegalArgumentException("Zoned TIMESTAMP precision must be between 0 and 9");
        }
        this.precision = precision;
    }

    public int precision() {
        return precision;
    }

    @Override
    public ZonedTimestampType copy(boolean nullable) {
        return new ZonedTimestampType(nullable, precision);
    }

    @Override
    public String asSerializableString() {
        return withNullability("TIMESTAMP(" + precision + ") WITH TIME ZONE");
    }

    @Override
    public Class<?> getDefaultConversion() {
        return java.time.OffsetDateTime.class;
    }

    @Override
    protected Object parameters() {
        return precision;
    }
}
