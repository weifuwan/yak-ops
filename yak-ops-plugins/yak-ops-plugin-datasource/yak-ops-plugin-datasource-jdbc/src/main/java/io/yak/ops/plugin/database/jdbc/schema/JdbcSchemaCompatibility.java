package io.yak.ops.plugin.database.jdbc.schema;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TimeType;
import io.yak.ops.core.types.TimestampType;
import io.yak.ops.core.types.ZonedTimestampType;

/**
 * Safe JDBC source-to-target schema compatibility without a Transform.
 *
 * <p>Unknown Catalog capacities retain the existing non-blocking preview policy; an execution
 * cannot use an unresolved decimal until its runtime value representation is established.
 */
public final class JdbcSchemaCompatibility {

    private JdbcSchemaCompatibility() {}

    public static boolean isCompatible(Column source, Column target) {
        if (source == null || target == null) {
            return false;
        }
        if (source.nullable() && !target.nullable()) {
            return false;
        }

        LogicalTypeRoot sourceRoot = source.dataType().getTypeRoot();
        LogicalTypeRoot targetRoot = target.dataType().getTypeRoot();
        if (sourceRoot == targetRoot) {
            return sameTypeCompatible(source, target);
        }
        if (sourceRoot == LogicalTypeRoot.CHAR && targetRoot == LogicalTypeRoot.VARCHAR) {
            return capacityCompatible(source.length(), target.length());
        }
        if (sourceRoot == LogicalTypeRoot.BINARY && targetRoot == LogicalTypeRoot.VARBINARY) {
            return capacityCompatible(source.length(), target.length());
        }
        if (isInteger(sourceRoot) && isInteger(targetRoot)) {
            return integerRank(sourceRoot) <= integerRank(targetRoot);
        }
        if (isInteger(sourceRoot) && targetRoot == LogicalTypeRoot.DECIMAL) {
            return integerToDecimalCompatible(sourceRoot, target.dataType());
        }
        if (sourceRoot == LogicalTypeRoot.BOOLEAN && isInteger(targetRoot)) {
            return true;
        }
        if (sourceRoot == LogicalTypeRoot.BOOLEAN && targetRoot == LogicalTypeRoot.DECIMAL) {
            return booleanToDecimalCompatible(target.dataType());
        }
        if (sourceRoot == LogicalTypeRoot.DATE && targetRoot == LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE) {
            return true;
        }
        return sourceRoot == LogicalTypeRoot.FLOAT && targetRoot == LogicalTypeRoot.DOUBLE;
    }

    private static boolean sameTypeCompatible(Column source, Column target) {
        LogicalTypeRoot root = source.dataType().getTypeRoot();
        return switch (root) {
            case CHAR, VARCHAR, BINARY, VARBINARY -> capacityCompatible(source.length(), target.length());
            case DECIMAL -> decimalCompatible(source.dataType(), target.dataType());
            case TIME_WITHOUT_TIME_ZONE ->
                ((TimeType) source.dataType()).precision() <= ((TimeType) target.dataType()).precision();
            case TIMESTAMP_WITHOUT_TIME_ZONE ->
                ((TimestampType) source.dataType()).precision() <= ((TimestampType) target.dataType()).precision();
            case TIMESTAMP_WITH_TIME_ZONE ->
                ((ZonedTimestampType) source.dataType()).precision()
                        <= ((ZonedTimestampType) target.dataType()).precision();
            default -> true;
        };
    }

    private static boolean booleanToDecimalCompatible(LogicalType target) {
        Integer scale = LogicalTypes.decimalScale(target);
        Integer precision = LogicalTypes.decimalPrecision(target);
        if (scale != null && scale != 0) {
            return false;
        }
        return !positive(precision) || precision >= 1;
    }

    private static boolean integerToDecimalCompatible(LogicalTypeRoot source, LogicalType target) {
        Integer precision = LogicalTypes.decimalPrecision(target);
        if (!positive(precision)) {
            return true;
        }
        Integer scale = LogicalTypes.decimalScale(target);
        return precision - (scale == null ? 0 : scale) >= integerDigits(source);
    }

    private static boolean decimalCompatible(LogicalType source, LogicalType target) {
        Integer sourceScale = LogicalTypes.decimalScale(source);
        Integer targetScale = LogicalTypes.decimalScale(target);
        if (sourceScale != null && targetScale != null && sourceScale > targetScale) {
            return false;
        }
        Integer sourcePrecision = LogicalTypes.decimalPrecision(source);
        Integer targetPrecision = LogicalTypes.decimalPrecision(target);
        if (!positive(sourcePrecision) || !positive(targetPrecision)) {
            return true;
        }
        if (sourceScale != null && targetScale != null) {
            return targetPrecision - targetScale >= sourcePrecision - sourceScale;
        }
        return targetPrecision >= sourcePrecision;
    }

    private static boolean capacityCompatible(Integer sourceSize, Integer targetSize) {
        return !positive(sourceSize) || !positive(targetSize) || targetSize >= sourceSize;
    }

    private static boolean isInteger(LogicalTypeRoot kind) {
        return kind == LogicalTypeRoot.TINYINT
                || kind == LogicalTypeRoot.SMALLINT
                || kind == LogicalTypeRoot.INTEGER
                || kind == LogicalTypeRoot.BIGINT;
    }

    private static int integerRank(LogicalTypeRoot kind) {
        return switch (kind) {
            case TINYINT -> 1;
            case SMALLINT -> 2;
            case INTEGER -> 3;
            case BIGINT -> 4;
            default -> throw new IllegalArgumentException("Not an integer type: " + kind);
        };
    }

    private static int integerDigits(LogicalTypeRoot kind) {
        return switch (kind) {
            case TINYINT -> 3;
            case SMALLINT -> 5;
            case INTEGER -> 10;
            case BIGINT -> 19;
            default -> throw new IllegalArgumentException("Not an integer type: " + kind);
        };
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
