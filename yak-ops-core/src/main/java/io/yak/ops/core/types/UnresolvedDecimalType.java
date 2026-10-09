package io.yak.ops.core.types;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * JDBC Catalog decimal metadata with missing or out-of-engine-range precision.
 *
 * <p>This type preserves source metadata for previews and target DDL diagnostics, but must not
 * enter an executing RowData conversion until explicitly resolved to a supported DecimalType.
 */
public final class UnresolvedDecimalType extends LogicalType {

    private final Integer precision;
    private final Integer scale;

    public UnresolvedDecimalType(Integer precision, Integer scale) {
        this(true, precision, scale);
    }

    public UnresolvedDecimalType(boolean nullable, Integer precision, Integer scale) {
        super(nullable, LogicalTypeRoot.DECIMAL);
        if (precision != null && precision <= 0) {
            throw new IllegalArgumentException("Decimal metadata precision must be positive");
        }
        if (scale != null && scale < 0) {
            throw new IllegalArgumentException("Decimal metadata scale must not be negative");
        }
        if (precision != null && scale != null && scale > precision) {
            throw new IllegalArgumentException("Decimal metadata scale must not exceed precision");
        }
        if (precision != null && scale != null && precision <= DecimalType.MAX_PRECISION) {
            throw new IllegalArgumentException("Resolved decimals must use DecimalType");
        }
        this.precision = precision;
        this.scale = scale;
    }

    public Integer precision() {
        return precision;
    }

    public Integer scale() {
        return scale;
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public UnresolvedDecimalType copy(boolean nullable) {
        return new UnresolvedDecimalType(nullable, precision, scale);
    }

    @Override
    public String asSerializableString() {
        String p = precision == null ? "?" : precision.toString();
        String s = scale == null ? "?" : scale.toString();
        return withNullability("UNRESOLVED_DECIMAL(" + p + ", " + s + ")");
    }

    @Override
    public Class<?> getDefaultConversion() {
        return BigDecimal.class;
    }

    @Override
    protected Object parameters() {
        return Arrays.asList(precision, scale);
    }
}
