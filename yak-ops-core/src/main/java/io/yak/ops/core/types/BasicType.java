package io.yak.ops.core.types;

import java.util.Objects;

/** Logical type without additional precision or scale parameters. */
public record BasicType(TypeKind kind) implements LogicalType {

    public BasicType {
        Objects.requireNonNull(kind, "kind");
        if (kind == TypeKind.DECIMAL) {
            throw new IllegalArgumentException("DECIMAL must use DecimalType");
        }
    }
}
