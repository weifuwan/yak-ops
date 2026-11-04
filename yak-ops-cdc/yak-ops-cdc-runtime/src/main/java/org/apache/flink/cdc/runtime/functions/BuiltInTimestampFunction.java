package org.apache.flink.cdc.runtime.functions;

import org.apache.flink.annotation.Internal;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlOperatorBinding;
import org.apache.calcite.sql.fun.SqlAbstractTimeFunction;
import org.apache.calcite.sql.type.SqlTypeName;

/**
 * Function that used to define SQL time function like LOCALTIMESTAMP, CURRENT_TIMESTAMP,
 * CURRENT_ROW_TIMESTAMP(), NOW() in Flink, the function support configuring the return type and the
 * precision of return type.
 */
@Internal
public class BuiltInTimestampFunction extends SqlAbstractTimeFunction {

    private final SqlTypeName returnTypeName;
    private final int precision;

    public BuiltInTimestampFunction(
            String functionName, SqlTypeName returnTypeName, int precision) {
        // access protected constructor
        super(functionName, returnTypeName);
        this.returnTypeName = returnTypeName;
        this.precision = precision;
    }

    @Override
    public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
        return opBinding.getTypeFactory().createSqlType(returnTypeName, precision);
    }

    @Override
    public boolean isDeterministic() {
        return false;
    }
}
