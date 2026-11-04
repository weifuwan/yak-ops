package org.apache.flink.cdc.runtime.parser.metadata;

import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.RowType;
import org.apache.flink.cdc.runtime.ai.AiEmbeddingFunctionDef;
import org.apache.flink.cdc.runtime.ai.AiImageFunctionDef;
import org.apache.flink.cdc.runtime.ai.AiTextFunctionDef;
import org.apache.flink.cdc.runtime.typeutils.CalciteDataTypeConverter;

import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlOperatorTables;

import java.util.ArrayList;
import java.util.List;

/** Creates an SqlOperatorTable containing the built-in AI functions. */
public class AiFunctionSqlOperatorTable {

    private AiFunctionSqlOperatorTable() {}

    /** Creates an SqlOperatorTable containing all AI functions defined in AiFunctionDef. */
    public static SqlOperatorTable create() {
        List<SqlFunction> functions = new ArrayList<>();
        for (AiTextFunctionDef def : AiTextFunctionDef.values()) {
            functions.add(createTextSqlFunction(def));
        }
        for (AiEmbeddingFunctionDef def : AiEmbeddingFunctionDef.values()) {
            functions.add(createEmbeddingSqlFunction(def));
        }
        for (AiImageFunctionDef def : AiImageFunctionDef.values()) {
            functions.add(createImageSqlFunction(def));
        }
        return SqlOperatorTables.of(functions);
    }

    private static SqlFunction createTextSqlFunction(AiTextFunctionDef def) {
        return new SqlFunction(
                def.getFunctionName(),
                SqlKind.OTHER_FUNCTION,
                ReturnTypes.explicit(SqlTypeName.VARIANT),
                null,
                OperandTypes.family(toSqlTypeFamiliesWithAdditionalParams(def.getInputType())),
                SqlFunctionCategory.USER_DEFINED_FUNCTION);
    }

    private static SqlFunction createEmbeddingSqlFunction(AiEmbeddingFunctionDef def) {
        return new SqlFunction(
                def.getFunctionName(),
                SqlKind.OTHER_FUNCTION,
                opBinding ->
                        CalciteDataTypeConverter.convertCalciteType(
                                opBinding.getTypeFactory(), def.getOutputType()),
                null,
                OperandTypes.family(SqlTypeFamily.STRING, toSqlTypeFamily(def.getInputType())),
                SqlFunctionCategory.USER_DEFINED_FUNCTION);
    }

    private static SqlFunction createImageSqlFunction(AiImageFunctionDef def) {
        return new SqlFunction(
                def.getFunctionName(),
                SqlKind.OTHER_FUNCTION,
                opBinding ->
                        CalciteDataTypeConverter.convertCalciteType(
                                opBinding.getTypeFactory(), def.getOutputType()),
                null,
                OperandTypes.family(toSqlTypeFamiliesWithModelArgument(def.getInputType())),
                SqlFunctionCategory.USER_DEFINED_FUNCTION);
    }

    /**
     * Converts inputType to SqlTypeFamily array, prepending additional parameters: modelName
     * (STRING) and input (STRING).
     */
    private static SqlTypeFamily[] toSqlTypeFamiliesWithAdditionalParams(RowType inputType) {
        List<SqlTypeFamily> families = new ArrayList<>();
        families.add(SqlTypeFamily.STRING); // modelName
        families.add(SqlTypeFamily.STRING); // input
        for (DataType fieldType : inputType.getFieldTypes()) {
            families.add(toSqlTypeFamily(fieldType));
        }
        return families.toArray(new SqlTypeFamily[0]);
    }

    private static SqlTypeFamily[] toSqlTypeFamiliesWithModelArgument(RowType inputType) {
        List<SqlTypeFamily> families = new ArrayList<>();
        families.add(SqlTypeFamily.STRING);
        for (DataType fieldType : inputType.getFieldTypes()) {
            families.add(toSqlTypeFamily(fieldType));
        }
        return families.toArray(new SqlTypeFamily[0]);
    }

    private static SqlTypeFamily toSqlTypeFamily(DataType dataType) {
        switch (dataType.getTypeRoot()) {
            case VARCHAR:
            case CHAR:
                return SqlTypeFamily.STRING;
            case INTEGER:
                return SqlTypeFamily.INTEGER;
            case BIGINT:
                return SqlTypeFamily.NUMERIC;
            case FLOAT:
            case DOUBLE:
                return SqlTypeFamily.APPROXIMATE_NUMERIC;
            case BOOLEAN:
                return SqlTypeFamily.BOOLEAN;
            case BINARY:
            case VARBINARY:
                return SqlTypeFamily.BINARY;
            default:
                return SqlTypeFamily.ANY;
        }
    }
}
