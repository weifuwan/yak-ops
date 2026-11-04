package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Implementation of {@link DataTypeVisitor} that redirects all calls to {@link
 * DataTypeDefaultVisitor#defaultMethod(DataType)}.
 */
@PublicEvolving
public abstract class DataTypeDefaultVisitor<R> implements DataTypeVisitor<R> {
    @Override
    public R visit(CharType charType) {
        return defaultMethod(charType);
    }

    @Override
    public R visit(VarCharType varCharType) {
        return defaultMethod(varCharType);
    }

    @Override
    public R visit(BooleanType booleanType) {
        return defaultMethod(booleanType);
    }

    @Override
    public R visit(BinaryType binaryType) {
        return defaultMethod(binaryType);
    }

    @Override
    public R visit(VarBinaryType bytesType) {
        return defaultMethod(bytesType);
    }

    @Override
    public R visit(DecimalType decimalType) {
        return defaultMethod(decimalType);
    }

    @Override
    public R visit(TinyIntType tinyIntType) {
        return defaultMethod(tinyIntType);
    }

    @Override
    public R visit(SmallIntType smallIntType) {
        return defaultMethod(smallIntType);
    }

    @Override
    public R visit(IntType intType) {
        return defaultMethod(intType);
    }

    @Override
    public R visit(BigIntType bigIntType) {
        return defaultMethod(bigIntType);
    }

    @Override
    public R visit(FloatType floatType) {
        return defaultMethod(floatType);
    }

    @Override
    public R visit(DoubleType doubleType) {
        return defaultMethod(doubleType);
    }

    @Override
    public R visit(DateType dateType) {
        return defaultMethod(dateType);
    }

    @Override
    public R visit(TimeType timeType) {
        return defaultMethod(timeType);
    }

    @Override
    public R visit(TimestampType timestampType) {
        return defaultMethod(timestampType);
    }

    @Override
    public R visit(ZonedTimestampType zonedTimestampType) {
        return defaultMethod(zonedTimestampType);
    }

    @Override
    public R visit(LocalZonedTimestampType localZonedTimestampType) {
        return defaultMethod(localZonedTimestampType);
    }

    @Override
    public R visit(ArrayType arrayType) {
        return defaultMethod(arrayType);
    }

    @Override
    public R visit(MapType mapType) {
        return defaultMethod(mapType);
    }

    @Override
    public R visit(RowType rowType) {
        return defaultMethod(rowType);
    }

    protected abstract R defaultMethod(DataType dataType);
}
