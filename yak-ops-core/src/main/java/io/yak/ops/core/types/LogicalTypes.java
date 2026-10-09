package io.yak.ops.core.types;

/** Common resolved type instances and metadata-aware factories. */
public final class LogicalTypes {

    public static final BasicType BOOLEAN = new BasicType(LogicalTypeRoot.BOOLEAN);
    public static final BasicType TINYINT = new BasicType(LogicalTypeRoot.TINYINT);
    public static final BasicType SMALLINT = new BasicType(LogicalTypeRoot.SMALLINT);
    public static final BasicType INTEGER = new BasicType(LogicalTypeRoot.INTEGER);
    public static final BasicType BIGINT = new BasicType(LogicalTypeRoot.BIGINT);
    public static final BasicType FLOAT = new BasicType(LogicalTypeRoot.FLOAT);
    public static final BasicType DOUBLE = new BasicType(LogicalTypeRoot.DOUBLE);
    public static final BasicType DATE = new BasicType(LogicalTypeRoot.DATE);
    public static final VarCharType STRING = new VarCharType(VarCharType.MAX_LENGTH);
    public static final VarBinaryType BINARY = new VarBinaryType(VarBinaryType.MAX_LENGTH);
    public static final TimeType TIME = new TimeType(6);
    public static final TimestampType TIMESTAMP = new TimestampType(6);
    public static final ZonedTimestampType TIMESTAMP_WITH_TIME_ZONE = new ZonedTimestampType(6);

    private LogicalTypes() {}

    public static DecimalType decimal(int precision, int scale) {
        return new DecimalType(precision, scale);
    }

    /** An unknown or out-of-engine-range JDBC decimal remains explicitly unresolved metadata. */
    public static LogicalType decimal(Integer precision, Integer scale) {
        if (precision != null && scale != null && precision <= DecimalType.MAX_PRECISION) {
            return new DecimalType(precision, scale);
        }
        return new UnresolvedDecimalType(precision, scale);
    }

    public static VarCharType varchar(int length) {
        return new VarCharType(length);
    }

    public static CharType charType(int length) {
        return new CharType(length);
    }

    public static VarBinaryType varbinary(int length) {
        return new VarBinaryType(length);
    }

    public static BinaryType fixedBinary(int length) {
        return new BinaryType(length);
    }

    public static TimeType time(int precision) {
        return new TimeType(precision);
    }

    public static TimestampType timestamp(int precision) {
        return new TimestampType(precision);
    }

    public static ZonedTimestampType zonedTimestamp(int precision) {
        return new ZonedTimestampType(precision);
    }

    /**
     * Projects a Catalog column's nullability and length into a resolved logical type.
     *
     * <p>Unknown/zero capacity remains unbounded rather than silently becoming VARCHAR(1).
     */
    public static LogicalType forColumn(LogicalType type, boolean nullable, Integer length) {
        if (length != null && length < 0) {
            throw new IllegalArgumentException("Column length must not be negative");
        }
        if (length == null || length == 0) {
            return type.copy(nullable);
        }
        return switch (type.getTypeRoot()) {
            case CHAR -> new CharType(nullable, length);
            case VARCHAR -> new VarCharType(nullable, length);
            case BINARY -> new BinaryType(nullable, length);
            case VARBINARY -> new VarBinaryType(nullable, length);
            default -> throw new IllegalArgumentException("Length is not applicable to type " + type.getTypeRoot());
        };
    }

    public static Integer decimalPrecision(LogicalType type) {
        if (type instanceof DecimalType decimal) {
            return decimal.precision();
        }
        if (type instanceof UnresolvedDecimalType decimal) {
            return decimal.precision();
        }
        throw new IllegalArgumentException("Expected DECIMAL logical type");
    }

    public static Integer decimalScale(LogicalType type) {
        if (type instanceof DecimalType decimal) {
            return decimal.scale();
        }
        if (type instanceof UnresolvedDecimalType decimal) {
            return decimal.scale();
        }
        throw new IllegalArgumentException("Expected DECIMAL logical type");
    }
}
