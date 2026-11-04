package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Data type of a decimal number with fixed precision and scale. */
@PublicEvolving
public final class DecimalType extends DataType {

    private static final long serialVersionUID = 1L;

    public static final int MIN_PRECISION = 1;

    public static final int MAX_PRECISION = 38;

    public static final int DEFAULT_PRECISION = 10;

    public static final int MIN_SCALE = 0;

    public static final int DEFAULT_SCALE = 0;

    private static final String FORMAT = "DECIMAL(%d, %d)";

    private final int precision;

    private final int scale;

    public DecimalType(boolean isNullable, int precision, int scale) {
        super(isNullable, DataTypeRoot.DECIMAL);
        if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(
                    String.format(
                            "Decimal precision must be between %d and %d (both inclusive).",
                            MIN_PRECISION, MAX_PRECISION));
        }
        if (scale < MIN_SCALE || scale > precision) {
            throw new IllegalArgumentException(
                    String.format(
                            "Decimal scale must be between %d and the precision %d (both inclusive).",
                            MIN_SCALE, precision));
        }
        this.precision = precision;
        this.scale = scale;
    }

    public DecimalType(int precision, int scale) {
        this(true, precision, scale);
    }

    public DecimalType(int precision) {
        this(precision, DEFAULT_SCALE);
    }

    public DecimalType() {
        this(DEFAULT_PRECISION);
    }

    public int getPrecision() {
        return precision;
    }

    public int getScale() {
        return scale;
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new DecimalType(isNullable, precision, scale);
    }

    @Override
    public String asSerializableString() {
        return withNullability(FORMAT, precision, scale);
    }

    @Override
    public List<DataType> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public <R> R accept(DataTypeVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        DecimalType that = (DecimalType) o;
        return precision == that.precision && scale == that.scale;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), precision, scale);
    }
}
