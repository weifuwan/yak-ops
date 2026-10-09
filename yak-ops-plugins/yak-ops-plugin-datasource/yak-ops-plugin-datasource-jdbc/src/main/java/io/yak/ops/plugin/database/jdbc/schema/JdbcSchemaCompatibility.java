package io.yak.ops.plugin.database.jdbc.schema;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.TypeKind;

/**
 * 定义 JDBC bounded 同步在 YakFlow 逻辑字段之间的可写兼容边界。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public final class JdbcSchemaCompatibility {

    private JdbcSchemaCompatibility() {}

    public static boolean isCompatible(Column source, Column target) {
        if (source == null || target == null) return false;
        if (source.nullable() && !target.nullable()) return false;

        TypeKind sourceKind = source.dataType().kind();
        TypeKind targetKind = target.dataType().kind();
        if (sourceKind == targetKind) {
            return sameTypeCompatible(source, target);
        }
        if (isInteger(sourceKind) && isInteger(targetKind)) {
            return integerRank(sourceKind) <= integerRank(targetKind);
        }
        if (isInteger(sourceKind) && targetKind == TypeKind.DECIMAL) {
            return integerToDecimalCompatible(sourceKind, (DecimalType) target.dataType());
        }
        if (sourceKind == TypeKind.BOOLEAN && isInteger(targetKind)) {
            return true;
        }
        if (sourceKind == TypeKind.BOOLEAN && targetKind == TypeKind.DECIMAL) {
            return booleanToDecimalCompatible((DecimalType) target.dataType());
        }
        if (sourceKind == TypeKind.DATE && targetKind == TypeKind.TIMESTAMP) {
            return true;
        }
        return sourceKind == TypeKind.FLOAT && targetKind == TypeKind.DOUBLE;
    }

    private static boolean sameTypeCompatible(Column source, Column target) {
        TypeKind kind = source.dataType().kind();
        if (kind == TypeKind.STRING || kind == TypeKind.BINARY) {
            return capacityCompatible(source.length(), target.length());
        }
        if (kind == TypeKind.DECIMAL) {
            return decimalCompatible((DecimalType) source.dataType(), (DecimalType) target.dataType());
        }
        return true;
    }

    private static boolean booleanToDecimalCompatible(DecimalType target) {
        if (knownScale(target.scale()) && target.scale() != 0) return false;
        return !positive(target.precision()) || target.precision() >= 1;
    }

    private static boolean integerToDecimalCompatible(TypeKind sourceKind, DecimalType target) {
        if (!positive(target.precision())) return true;

        int targetScale = knownScale(target.scale()) ? target.scale() : 0;
        int targetIntegerDigits = target.precision() - targetScale;
        return targetIntegerDigits >= integerDigits(sourceKind);
    }

    private static boolean decimalCompatible(DecimalType source, DecimalType target) {
        if (knownScale(source.scale()) && knownScale(target.scale()) && source.scale() > target.scale()) {
            return false;
        }
        if (!positive(source.precision()) || !positive(target.precision())) return true;

        if (knownScale(source.scale()) && knownScale(target.scale())) {
            int sourceIntegerDigits = source.precision() - source.scale();
            int targetIntegerDigits = target.precision() - target.scale();
            return targetIntegerDigits >= sourceIntegerDigits;
        }
        return target.precision() >= source.precision();
    }

    private static boolean capacityCompatible(Integer sourceSize, Integer targetSize) {
        return !positive(sourceSize) || !positive(targetSize) || targetSize >= sourceSize;
    }

    private static boolean isInteger(TypeKind kind) {
        return kind == TypeKind.TINYINT
                || kind == TypeKind.SMALLINT
                || kind == TypeKind.INTEGER
                || kind == TypeKind.BIGINT;
    }

    private static int integerRank(TypeKind kind) {
        return switch (kind) {
            case TINYINT -> 1;
            case SMALLINT -> 2;
            case INTEGER -> 3;
            case BIGINT -> 4;
            default -> throw new IllegalArgumentException("not an integer type: " + kind);
        };
    }

    private static int integerDigits(TypeKind kind) {
        return switch (kind) {
            case TINYINT -> 3;
            case SMALLINT -> 5;
            case INTEGER -> 10;
            case BIGINT -> 19;
            default -> throw new IllegalArgumentException("not an integer type: " + kind);
        };
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static boolean knownScale(Integer value) {
        return value != null && value >= 0;
    }
}
