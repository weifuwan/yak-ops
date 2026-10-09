package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implements metadata-derived nullable JDBC field codecs with a single internal value contract.
 *
 * <p>Vendor subclasses may resolve uncommon SQL types or override conversions. Decimal
 * precision, scale, and integer boundaries are checked without lossy rounding or truncation.
 */
public abstract class AbstractDialectConverter implements JdbcDialectConverter {

    private final TableSchema schema;

    protected AbstractDialectConverter(ResultSetMetaData metadata) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        List<Column> columns = new ArrayList<>(metadata.getColumnCount());
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String name = metadata.getColumnLabel(index);
            LogicalType type = resolveType(metadata, index);
            if (type == null || !type.isResolved()) {
                throw new SQLFeatureNotSupportedException(
                        "JDBC field '" + name + "' has an unsupported or unresolved logical type");
            }
            boolean nullable = metadata.isNullable(index) != ResultSetMetaData.columnNoNulls;
            columns.add(new Column(name, type.copy(nullable)));
        }
        this.schema = new TableSchema(columns, List.of());
    }

    @Override
    public final TableSchema schema() {
        return schema;
    }

    @Override
    public final RowData toInternal(ResultSet resultSet) throws SQLException {
        GenericRowData row = new GenericRowData(schema.columnCount());
        for (int index = 0; index < schema.columnCount(); index++) {
            LogicalType type = schema.column(index).dataType();
            Object value = readField(resultSet, index + 1, type);
            if (value == null && !type.isNullable()) {
                throw new SQLDataException("Null value in non-null JDBC field: "
                        + schema.column(index).name());
            }
            row.setField(index, value);
        }
        return row;
    }

    @Override
    public final void toExternal(RowData row, PreparedStatement statement) throws SQLException {
        if (row.getArity() != schema.columnCount()) {
            throw new SQLDataException("JDBC statement field count does not match RowData arity");
        }
        for (int index = 0; index < schema.columnCount(); index++) {
            LogicalType type = schema.column(index).dataType();
            int position = index + 1;
            if (row.isNullAt(index)) {
                if (!type.isNullable()) {
                    throw new SQLDataException("Null value in required JDBC field: "
                            + schema.column(index).name());
                }
                statement.setNull(position, sqlType(type));
                continue;
            }
            try {
                switch (type.getTypeRoot()) {
                    case BOOLEAN -> statement.setBoolean(position, row.getBoolean(index));
                    case TINYINT -> statement.setByte(position, row.getByte(index));
                    case SMALLINT -> statement.setShort(position, row.getShort(index));
                    case INTEGER -> statement.setInt(position, row.getInt(index));
                    case BIGINT -> statement.setLong(position, row.getLong(index));
                    case FLOAT -> statement.setFloat(position, row.getFloat(index));
                    case DOUBLE -> statement.setDouble(position, row.getDouble(index));
                    case DECIMAL -> bindDecimal(row, statement, index, position, (DecimalType) type);
                    case CHAR, VARCHAR -> statement.setString(position, row.getString(index));
                    case BINARY, VARBINARY -> statement.setBytes(position, row.getBinary(index));
                    case DATE -> statement.setDate(position, java.sql.Date.valueOf(row.getDate(index)));
                    case TIME_WITHOUT_TIME_ZONE -> statement.setObject(position, row.getTime(index, 9));
                    case TIMESTAMP_WITHOUT_TIME_ZONE ->
                        statement.setTimestamp(position, java.sql.Timestamp.valueOf(row.getTimestamp(index, 9)));
                    case TIMESTAMP_WITH_TIME_ZONE -> statement.setObject(position, row.getZonedTimestamp(index, 9));
                }
            } catch (ClassCastException | ArithmeticException exception) {
                throw new SQLDataException(
                        "Invalid internal JDBC value for field "
                                + schema.column(index).name(),
                        exception);
            }
        }
    }

    private static void bindDecimal(RowData row, PreparedStatement statement, int index, int position, DecimalType type)
            throws SQLException {
        BigDecimal amount = row.getDecimal(index, type.precision(), type.scale());
        statement.setBigDecimal(position, decimalValue(amount, type));
    }

    /**
     * Resolves a JDBC result-column type without inventing a DECIMAL precision.
     *
     * <p>Unsupported nested, vendor-specific, or ambiguous SQL types must be overridden
     * explicitly in a vendor converter or rejected before creating rows.
     */
    protected LogicalType resolveType(ResultSetMetaData metadata, int index) throws SQLException {
        int sqlType = metadata.getColumnType(index);
        int precision = metadata.getPrecision(index);
        int scale = metadata.getScale(index);
        return switch (sqlType) {
            case Types.BOOLEAN -> LogicalTypes.BOOLEAN;
            case Types.BIT -> precision <= 1 ? LogicalTypes.BOOLEAN : LogicalTypes.varbinary(precision);
            case Types.TINYINT -> LogicalTypes.TINYINT;
            case Types.SMALLINT -> LogicalTypes.SMALLINT;
            case Types.INTEGER -> LogicalTypes.INTEGER;
            case Types.BIGINT -> LogicalTypes.BIGINT;
            case Types.REAL, Types.FLOAT -> LogicalTypes.FLOAT;
            case Types.DOUBLE -> LogicalTypes.DOUBLE;
            case Types.NUMERIC, Types.DECIMAL -> decimalType(precision, scale, metadata.getColumnLabel(index));
            case Types.CHAR, Types.NCHAR -> precision > 0 ? LogicalTypes.charType(precision) : LogicalTypes.STRING;
            case Types.VARCHAR, Types.NVARCHAR -> precision > 0 ? LogicalTypes.varchar(precision) : LogicalTypes.STRING;
            case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> LogicalTypes.STRING;
            case Types.BINARY -> precision > 0 ? LogicalTypes.fixedBinary(precision) : LogicalTypes.BINARY;
            case Types.VARBINARY -> precision > 0 ? LogicalTypes.varbinary(precision) : LogicalTypes.BINARY;
            case Types.LONGVARBINARY, Types.BLOB -> LogicalTypes.BINARY;
            case Types.DATE -> LogicalTypes.DATE;
            case Types.TIME -> LogicalTypes.time(temporalPrecision(scale));
            case Types.TIMESTAMP -> LogicalTypes.timestamp(temporalPrecision(scale));
            case Types.TIMESTAMP_WITH_TIMEZONE -> LogicalTypes.zonedTimestamp(temporalPrecision(scale));
            default ->
                throw new SQLFeatureNotSupportedException("Unsupported JDBC type " + metadata.getColumnTypeName(index)
                        + " for field " + metadata.getColumnLabel(index));
        };
    }

    /** Converts a JDBC ResultSet field to the exact Core representation for its logical type. */
    protected Object readField(ResultSet resultSet, int position, LogicalType type) throws SQLException {
        Object raw = resultSet.getObject(position);
        if (raw == null) {
            return null;
        }
        try {
            return switch (type.getTypeRoot()) {
                case BOOLEAN -> resultSet.getBoolean(position);
                case TINYINT -> byteValue(raw);
                case SMALLINT -> shortValue(raw);
                case INTEGER -> integerValue(raw);
                case BIGINT -> longValue(raw);
                case FLOAT -> numeric(raw).floatValue();
                case DOUBLE -> numeric(raw).doubleValue();
                case DECIMAL -> decimalValue(resultSet.getBigDecimal(position), (DecimalType) type);
                case CHAR, VARCHAR -> resultSet.getString(position);
                case BINARY, VARBINARY -> resultSet.getBytes(position);
                case DATE -> localDate(raw, resultSet, position);
                case TIME_WITHOUT_TIME_ZONE -> localTime(raw, resultSet, position);
                case TIMESTAMP_WITHOUT_TIME_ZONE -> localDateTime(raw, resultSet, position);
                case TIMESTAMP_WITH_TIME_ZONE -> offsetDateTime(raw, resultSet, position);
            };
        } catch (ArithmeticException | ClassCastException exception) {
            throw new SQLDataException("JDBC value cannot be represented as " + type.asSerializableString(), exception);
        }
    }

    protected static LogicalType decimalType(int precision, int scale, String column) throws SQLDataException {
        if (precision < 1 || precision > DecimalType.MAX_PRECISION || scale < 0 || scale > precision) {
            throw new SQLDataException("Unresolved or unsupported JDBC decimal precision for field " + column);
        }
        return LogicalTypes.decimal(precision, scale);
    }

    protected static int temporalPrecision(int precision) throws SQLDataException {
        if (precision < 0 || precision > 9) {
            throw new SQLDataException("JDBC temporal precision outside 0..9");
        }
        return precision;
    }

    protected static byte byteValue(Object value) {
        return numeric(value).byteValueExact();
    }

    protected static short shortValue(Object value) {
        return numeric(value).shortValueExact();
    }

    protected static int integerValue(Object value) {
        return numeric(value).intValueExact();
    }

    protected static long longValue(Object value) {
        return numeric(value).longValueExact();
    }

    protected static BigDecimal numeric(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new ClassCastException("Expected numeric value");
    }

    private static BigDecimal decimalValue(BigDecimal decimal, DecimalType type) throws SQLDataException {
        if (decimal == null) {
            return null;
        }
        try {
            BigDecimal value = decimal.setScale(type.scale(), RoundingMode.UNNECESSARY);
            if (value.precision() > type.precision()) {
                throw new SQLDataException("JDBC decimal exceeds declared precision");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new SQLDataException("JDBC decimal requires precision-losing rounding", exception);
        }
    }

    private static LocalDate localDate(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        java.sql.Date date = resultSet.getDate(index);
        return date == null ? null : date.toLocalDate();
    }

    private static LocalTime localTime(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof LocalTime time) {
            return time;
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime();
        }
        Object mapped = resultSet.getObject(index, LocalTime.class);
        return (LocalTime) mapped;
    }

    private static LocalDateTime localDateTime(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof LocalDateTime timestamp) {
            return timestamp;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        java.sql.Timestamp timestamp = resultSet.getTimestamp(index);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static OffsetDateTime offsetDateTime(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }
        return resultSet.getObject(index, OffsetDateTime.class);
    }

    private static int sqlType(LogicalType type) {
        return switch (type.getTypeRoot()) {
            case BOOLEAN -> Types.BOOLEAN;
            case TINYINT -> Types.TINYINT;
            case SMALLINT -> Types.SMALLINT;
            case INTEGER -> Types.INTEGER;
            case BIGINT -> Types.BIGINT;
            case FLOAT -> Types.REAL;
            case DOUBLE -> Types.DOUBLE;
            case DECIMAL -> Types.DECIMAL;
            case CHAR -> Types.CHAR;
            case VARCHAR -> Types.VARCHAR;
            case BINARY -> Types.BINARY;
            case VARBINARY -> Types.VARBINARY;
            case DATE -> Types.DATE;
            case TIME_WITHOUT_TIME_ZONE -> Types.TIME;
            case TIMESTAMP_WITHOUT_TIME_ZONE -> Types.TIMESTAMP;
            case TIMESTAMP_WITH_TIME_ZONE -> Types.TIMESTAMP_WITH_TIMEZONE;
        };
    }
}
