package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.DecimalType;

/** Shared native numeric and length type rules across JDBC target dialects. */
final class JdbcTypeMappings {

    private JdbcTypeMappings() {}

    static JdbcNativeType decimal(Column column, String nativeName, int maxPrecision, int maxScale) {
        DecimalType decimal = (DecimalType) column.dataType();
        Integer precision = decimal.precision();
        Integer scale = decimal.scale();

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

    static boolean knownLength(Integer length) {
        return length != null && length > 0;
    }
}
