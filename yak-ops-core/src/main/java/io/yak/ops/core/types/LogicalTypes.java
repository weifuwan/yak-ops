package io.yak.ops.core.types;

/** Shared immutable logical types and parameterized decimal factory methods. */
public final class LogicalTypes {

    public static final LogicalType BOOLEAN = new BasicType(TypeKind.BOOLEAN);
    public static final LogicalType TINYINT = new BasicType(TypeKind.TINYINT);
    public static final LogicalType SMALLINT = new BasicType(TypeKind.SMALLINT);
    public static final LogicalType INTEGER = new BasicType(TypeKind.INTEGER);
    public static final LogicalType BIGINT = new BasicType(TypeKind.BIGINT);
    public static final LogicalType FLOAT = new BasicType(TypeKind.FLOAT);
    public static final LogicalType DOUBLE = new BasicType(TypeKind.DOUBLE);
    public static final LogicalType STRING = new BasicType(TypeKind.STRING);
    public static final LogicalType BINARY = new BasicType(TypeKind.BINARY);
    public static final LogicalType DATE = new BasicType(TypeKind.DATE);
    public static final LogicalType TIME = new BasicType(TypeKind.TIME);
    public static final LogicalType TIMESTAMP = new BasicType(TypeKind.TIMESTAMP);
    public static final LogicalType TIMESTAMP_WITH_TIME_ZONE = new BasicType(TypeKind.TIMESTAMP_WITH_TIME_ZONE);

    private LogicalTypes() {}

    public static DecimalType decimal(Integer precision, Integer scale) {
        return new DecimalType(precision, scale);
    }
}
