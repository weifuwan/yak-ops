package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDecimalType;
import io.yak.ops.flow.api.row.YakTypeKind;

/**
 * 定义 JDBC bounded 同步在 YakFlow 逻辑字段之间的可写兼容边界。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public final class JdbcSchemaCompatibility {

    private JdbcSchemaCompatibility() {}

    public static boolean isCompatible(YakColumn source, YakColumn target) {
        if (source == null || target == null) return false;
        if (source.nullable() && !target.nullable()) return false;

        YakTypeKind sourceKind = source.dataType().kind();
        YakTypeKind targetKind = target.dataType().kind();
        if (sourceKind == targetKind) {
            return sameTypeCompatible(source, target);
        }
        if (isInteger(sourceKind) && isInteger(targetKind)) {
            return integerRank(sourceKind) <= integerRank(targetKind);
        }
        if (isInteger(sourceKind) && targetKind == YakTypeKind.DECIMAL) {
            return integerToDecimalCompatible(sourceKind, (YakDecimalType) target.dataType());
        }
        if (sourceKind == YakTypeKind.BOOLEAN && isInteger(targetKind)) {
            return true;
        }
        if (sourceKind == YakTypeKind.BOOLEAN && targetKind == YakTypeKind.DECIMAL) {
            return booleanToDecimalCompatible((YakDecimalType) target.dataType());
        }
        if (sourceKind == YakTypeKind.DATE && targetKind == YakTypeKind.TIMESTAMP) {
            return true;
        }
        return sourceKind == YakTypeKind.FLOAT && targetKind == YakTypeKind.DOUBLE;
    }

    private static boolean sameTypeCompatible(YakColumn source, YakColumn target) {
        YakTypeKind kind = source.dataType().kind();
        if (kind == YakTypeKind.STRING || kind == YakTypeKind.BINARY) {
            return capacityCompatible(source.length(), target.length());
        }
        if (kind == YakTypeKind.DECIMAL) {
            return decimalCompatible((YakDecimalType) source.dataType(), (YakDecimalType) target.dataType());
        }
        return true;
    }

    private static boolean booleanToDecimalCompatible(YakDecimalType target) {
        if (knownScale(target.scale()) && target.scale() != 0) return false;
        return !positive(target.precision()) || target.precision() >= 1;
    }

    private static boolean integerToDecimalCompatible(YakTypeKind sourceKind, YakDecimalType target) {
        if (!positive(target.precision())) return true;

        int targetScale = knownScale(target.scale()) ? target.scale() : 0;
        int targetIntegerDigits = target.precision() - targetScale;
        return targetIntegerDigits >= integerDigits(sourceKind);
    }

    private static boolean decimalCompatible(YakDecimalType source, YakDecimalType target) {
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

    private static boolean isInteger(YakTypeKind kind) {
        return kind == YakTypeKind.TINYINT
                || kind == YakTypeKind.SMALLINT
                || kind == YakTypeKind.INTEGER
                || kind == YakTypeKind.BIGINT;
    }

    private static int integerRank(YakTypeKind kind) {
        return switch (kind) {
            case TINYINT -> 1;
            case SMALLINT -> 2;
            case INTEGER -> 3;
            case BIGINT -> 4;
            default -> throw new IllegalArgumentException("not an integer type: " + kind);
        };
    }

    private static int integerDigits(YakTypeKind kind) {
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
