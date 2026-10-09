package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TimeType;
import io.yak.ops.core.types.TimestampType;
import io.yak.ops.core.types.ZonedTimestampType;

/** Shared vendor native-type planning without duplicating Core logical type semantics. */
final class JdbcTypeMappings {

    private JdbcTypeMappings() {}

    static JdbcNativeType decimal(Column column, String nativeName, int maxPrecision, int maxScale) {
        LogicalType decimal = column.dataType();
        Integer precision = LogicalTypes.decimalPrecision(decimal);
        Integer scale = LogicalTypes.decimalScale(decimal);

        if (precision != null && precision > maxPrecision) {
            throw new UnsupportedOperationException(nativeName + " 最大 precision=" + maxPrecision + "，当前为 " + precision);
        }
        if (scale != null && scale > maxScale) {
            throw new UnsupportedOperationException(nativeName + " 最大 scale=" + maxScale + "，当前为 " + scale);
        }
        if (precision != null && scale != null) {
            return JdbcNativeType.of(nativeName + "(" + precision + "," + scale + ")");
        }
        return JdbcNativeType.of(nativeName, "DECIMAL precision / scale 元数据不完整，目标使用未限定精度的 " + nativeName);
    }

    static int precision(Column column, int maximum, String nativeName) {
        int precision =
                switch (column.dataType()) {
                    case TimeType value -> value.precision();
                    case TimestampType value -> value.precision();
                    case ZonedTimestampType value -> value.precision();
                    default -> throw new IllegalArgumentException("Expected temporal logical type");
                };
        if (precision > maximum) {
            throw new UnsupportedOperationException(nativeName + " 最大时间精度=" + maximum + "，当前为 " + precision);
        }
        return precision;
    }

    static boolean knownLength(Integer length) {
        return length != null && length > 0;
    }
}
