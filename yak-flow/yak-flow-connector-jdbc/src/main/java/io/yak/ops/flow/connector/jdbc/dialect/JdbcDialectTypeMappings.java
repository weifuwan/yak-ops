package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDecimalType;

/** JDBC Dialect 共用的参数化逻辑类型规划规则。 */
final class JdbcDialectTypeMappings {

    private JdbcDialectTypeMappings() {}

    static JdbcNativeType decimal(YakColumn column, String nativeName, int maxPrecision, int maxScale) {
        YakDecimalType decimal = (YakDecimalType) column.dataType();
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
