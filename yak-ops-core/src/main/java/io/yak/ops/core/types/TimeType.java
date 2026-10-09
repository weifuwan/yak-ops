package io.yak.ops.core.types;

/** Local time with fractional-second precision from 0 to 9 digits. */
public final class TimeType extends LogicalType {

    private final int precision;

    public TimeType(int precision) {
        this(true, precision);
    }

    public TimeType(boolean nullable, int precision) {
        super(nullable, LogicalTypeRoot.TIME_WITHOUT_TIME_ZONE);
        if (precision < 0 || precision > 9) {
            throw new IllegalArgumentException("TIME precision must be between 0 and 9");
        }
        this.precision = precision;
    }

    public int precision() {
        return precision;
    }

    @Override
    public TimeType copy(boolean nullable) {
        return new TimeType(nullable, precision);
    }

    @Override
    public String asSerializableString() {
        return withNullability("TIME(" + precision + ")");
    }

    @Override
    public Class<?> getDefaultConversion() {
        return java.time.LocalTime.class;
    }

    @Override
    protected Object parameters() {
        return precision;
    }
}
