package io.yak.ops.core.types;

import java.math.BigDecimal;
import java.util.List;

/** Resolved fixed-precision decimal in YakFlow's 38-digit internal precision domain. */
public final class DecimalType extends LogicalType {

    public static final int MAX_PRECISION = 38;
    public static final int DEFAULT_PRECISION = 10;

    private final int precision;
    private final int scale;

    public DecimalType(int precision, int scale) {
        this(true, precision, scale);
    }

    public DecimalType(boolean nullable, int precision, int scale) {
        super(nullable, LogicalTypeRoot.DECIMAL);
        if (precision < 1 || precision > MAX_PRECISION) {
            throw new IllegalArgumentException("Decimal precision must be between 1 and " + MAX_PRECISION);
        }
        if (scale < 0 || scale > precision) {
            throw new IllegalArgumentException("Decimal scale must be between 0 and precision");
        }
        this.precision = precision;
        this.scale = scale;
    }

    public int precision() {
        return precision;
    }

    public int scale() {
        return scale;
    }

    @Override
    public DecimalType copy(boolean nullable) {
        return new DecimalType(nullable, precision, scale);
    }

    @Override
    public String asSerializableString() {
        return withNullability("DECIMAL(" + precision + ", " + scale + ")");
    }

    @Override
    public Class<?> getDefaultConversion() {
        return BigDecimal.class;
    }

    @Override
    protected Object parameters() {
        return List.of(precision, scale);
    }
}
