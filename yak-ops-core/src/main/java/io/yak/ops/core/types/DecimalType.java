package io.yak.ops.core.types;

/**
 * Decimal logical type with optional metadata precision and scale.
 *
 * <p>Unknown database metadata remains null rather than being invented.
 */
public record DecimalType(Integer precision, Integer scale) implements LogicalType {

    public DecimalType {
        if (precision != null && precision <= 0) {
            throw new IllegalArgumentException("precision must be positive");
        }
        if (scale != null && scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
        if (precision != null && scale != null && scale > precision) {
            throw new IllegalArgumentException("scale must not exceed precision");
        }
    }

    @Override
    public TypeKind kind() {
        return TypeKind.DECIMAL;
    }
}
