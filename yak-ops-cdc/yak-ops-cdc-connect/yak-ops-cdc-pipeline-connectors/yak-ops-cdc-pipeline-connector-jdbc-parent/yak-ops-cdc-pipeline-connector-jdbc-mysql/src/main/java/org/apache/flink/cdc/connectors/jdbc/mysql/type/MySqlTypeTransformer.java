package org.apache.flink.cdc.connectors.jdbc.mysql.type;

import org.apache.flink.cdc.common.types.BigIntType;
import org.apache.flink.cdc.common.types.BinaryType;
import org.apache.flink.cdc.common.types.BooleanType;
import org.apache.flink.cdc.common.types.CharType;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypeDefaultVisitor;
import org.apache.flink.cdc.common.types.DateType;
import org.apache.flink.cdc.common.types.DecimalType;
import org.apache.flink.cdc.common.types.DoubleType;
import org.apache.flink.cdc.common.types.FloatType;
import org.apache.flink.cdc.common.types.IntType;
import org.apache.flink.cdc.common.types.LocalZonedTimestampType;
import org.apache.flink.cdc.common.types.SmallIntType;
import org.apache.flink.cdc.common.types.TimeType;
import org.apache.flink.cdc.common.types.TimestampType;
import org.apache.flink.cdc.common.types.TinyIntType;
import org.apache.flink.cdc.common.types.VarBinaryType;
import org.apache.flink.cdc.common.types.VarCharType;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcColumn;

import com.mysql.cj.MysqlType;

/** MySQL type transformer from CDC {@link DataType}s to {@link JdbcColumn}. */
public class MySqlTypeTransformer extends DataTypeDefaultVisitor<JdbcColumn.Builder> {
    private final JdbcColumn.Builder builder;
    public static final int POWER_2_8 = (int) Math.pow(2, 8);
    public static final int POWER_2_16 = (int) Math.pow(2, 16);
    public static final int POWER_2_24 = (int) Math.pow(2, 24);
    public static final int POWER_2_32 = (int) Math.pow(2, 32);

    public MySqlTypeTransformer(JdbcColumn.Builder builder) {
        this.builder = builder;
    }

    @Override
    public JdbcColumn.Builder visit(CharType charType) {
        builder.length(charType.getLength());
        builder.dataType(MysqlType.CHAR.name());
        builder.columnType(String.format("CHAR(%d)", charType.getLength()));
        builder.isNullable(charType.isNullable());
        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(VarCharType varCharType) {
        int length = varCharType.getLength();

        if (length < POWER_2_16 - 1) {
            builder.dataType(MysqlType.VARCHAR.name());
            builder.columnType(String.format("%s(%s)", MysqlType.VARCHAR, length));
        } else if (length < POWER_2_24) {
            builder.dataType(MysqlType.MEDIUMTEXT.name());
            builder.columnType(MysqlType.MEDIUMTEXT.name());
        } else if (length < POWER_2_32) {
            builder.dataType(MysqlType.LONGTEXT.name());
            builder.columnType(MysqlType.LONGTEXT.name());
        } else {
            builder.dataType(MysqlType.TEXT.name());
            builder.columnType(MysqlType.TEXT.name());
        }

        builder.length(length);
        builder.isNullable(varCharType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(BooleanType booleanType) {
        builder.dataType(MysqlType.TINYINT.name());
        builder.columnType("TINYINT(1)");
        builder.isNullable(booleanType.isNullable());
        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(DecimalType decimalType) {
        int precision = decimalType.getPrecision();
        int scale = decimalType.getScale();

        builder.dataType(MysqlType.DECIMAL.name());
        builder.columnType(String.format("DECIMAL(%d,%d)", precision, scale));
        builder.length(precision);
        builder.scale(scale);
        builder.isNullable(decimalType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(TinyIntType tinyIntType) {
        builder.dataType(MysqlType.TINYINT.name());
        builder.columnType(MysqlType.TINYINT.name());
        builder.isNullable(tinyIntType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(SmallIntType smallIntType) {
        builder.dataType(MysqlType.SMALLINT.name());
        builder.columnType(MysqlType.SMALLINT.name());
        builder.isNullable(smallIntType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(IntType intType) {
        builder.dataType(MysqlType.INT.name());
        builder.columnType(MysqlType.INT.name());
        builder.isNullable(intType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(BigIntType bigIntType) {
        builder.dataType(MysqlType.BIGINT.name());
        builder.columnType(MysqlType.BIGINT.name());
        builder.isNullable(bigIntType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(FloatType floatType) {
        builder.dataType(MysqlType.FLOAT.name());
        builder.columnType(MysqlType.FLOAT.name());
        builder.isNullable(floatType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(DoubleType doubleType) {
        builder.dataType(MysqlType.DOUBLE.name());
        builder.columnType(MysqlType.DOUBLE.name());
        builder.isNullable(doubleType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(BinaryType binaryType) {
        builder.dataType(MysqlType.BINARY.name());
        builder.length(binaryType.getLength());
        builder.columnType(String.format("BINARY(%d)", binaryType.getLength()));
        builder.isNullable(binaryType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(VarBinaryType bytesType) {
        int length = bytesType.getLength();

        if (length <= POWER_2_16 - 1) {
            builder.dataType(MysqlType.VARBINARY.name());
            builder.columnType(String.format("%s(%d)", MysqlType.VARBINARY.name(), length));
        } else if (length < POWER_2_24) {
            builder.dataType(MysqlType.MEDIUMBLOB.name());
            builder.columnType(MysqlType.MEDIUMBLOB.name());
        } else if (length < POWER_2_32) {
            builder.dataType(MysqlType.LONGBLOB.name());
            builder.columnType(MysqlType.LONGBLOB.name());
        } else {
            builder.dataType(MysqlType.LONGBLOB.name());
            builder.columnType(MysqlType.LONGBLOB.name());
        }

        builder.length(length);
        builder.isNullable(bytesType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(DateType dateType) {
        builder.dataType(MysqlType.DATE.name());
        builder.columnType(MysqlType.DATE.name());
        builder.isNullable(dateType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(TimeType timeType) {
        int precision = timeType.getPrecision();
        builder.length(precision);
        builder.dataType(MysqlType.TIME.name());
        if (precision > 0) {
            builder.columnType(String.format("TIME(%d)", precision));
        } else {
            builder.columnType("TIME");
        }
        builder.isNullable(timeType.isNullable());

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(TimestampType timestampType) {
        int precision = timestampType.getPrecision();
        builder.dataType(MysqlType.DATETIME.name());
        builder.length(precision);
        builder.isNullable(timestampType.isNullable());
        if (precision > 0) {
            builder.columnType(String.format("DATETIME(%d)", precision));
        } else {
            builder.columnType("DATETIME");
        }

        return builder;
    }

    @Override
    public JdbcColumn.Builder visit(LocalZonedTimestampType localZonedTimestampType) {
        int precision = localZonedTimestampType.getPrecision();
        builder.dataType(MysqlType.TIMESTAMP.name());
        builder.length(precision);
        builder.isNullable(localZonedTimestampType.isNullable());
        if (precision > 0) {
            builder.columnType(String.format("TIMESTAMP(%d)", precision));
        } else {
            builder.columnType("TIMESTAMP");
        }

        return builder;
    }

    @Override
    protected JdbcColumn.Builder defaultMethod(DataType dataType) {
        throw new UnsupportedOperationException("Unsupported CDC data type " + dataType);
    }
}
