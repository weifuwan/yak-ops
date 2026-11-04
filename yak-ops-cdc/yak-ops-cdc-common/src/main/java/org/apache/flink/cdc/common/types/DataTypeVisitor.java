package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * The visitor definition of {@link DataType}. The visitor transforms a data type into instances of
 * {@code R}.
 *
 * @param <R> result type
 */
@PublicEvolving
public interface DataTypeVisitor<R> {

    R visit(CharType charType);

    R visit(VarCharType varCharType);

    R visit(BooleanType booleanType);

    R visit(BinaryType binaryType);

    R visit(VarBinaryType varBinaryType);

    R visit(DecimalType decimalType);

    R visit(TinyIntType tinyIntType);

    R visit(SmallIntType smallIntType);

    R visit(IntType intType);

    R visit(BigIntType bigIntType);

    R visit(FloatType floatType);

    R visit(DoubleType doubleType);

    R visit(DateType dateType);

    R visit(TimeType timeType);

    R visit(TimestampType timestampType);

    R visit(ZonedTimestampType zonedTimestampType);

    R visit(LocalZonedTimestampType localZonedTimestampType);

    R visit(ArrayType arrayType);

    R visit(MapType mapType);

    R visit(RowType rowType);

    default R visit(VariantType variantType) {
        throw new UnsupportedOperationException("VariantType was not supported.");
    }
}
