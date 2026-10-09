package io.yak.ops.plugin.database.jdbc.schema;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Maps JDBC Catalog metadata to Core logical types, preserving source column order and key order.
 *
 * <p>Unsupported or incomplete Decimal metadata is explicit, never replaced by an invented
 * DECIMAL(10,0). Vendor JDBC values are converted separately by the upcoming JDBC Converter.
 */
public final class JdbcSchemaMapper {

    private JdbcSchemaMapper() {}

    public static TableSchema fromColumns(List<DataSourceColumn> sourceColumns) {
        Objects.requireNonNull(sourceColumns, "sourceColumns must not be null");
        if (sourceColumns.isEmpty()) {
            throw new IllegalArgumentException("sourceColumns must not be empty");
        }

        List<DataSourceColumn> ordered = sourceColumns.stream()
                .sorted(Comparator.comparingInt(DataSourceColumn::ordinalPosition))
                .toList();
        List<Column> columns = new ArrayList<>(ordered.size());
        for (DataSourceColumn column : ordered) {
            columns.add(toColumn(column));
        }

        List<String> primaryKeys = ordered.stream()
                .filter(DataSourceColumn::primaryKey)
                .sorted(Comparator.comparingInt(JdbcSchemaMapper::primaryKeyOrder)
                        .thenComparingInt(DataSourceColumn::ordinalPosition))
                .map(DataSourceColumn::name)
                .toList();
        return new TableSchema(columns, primaryKeys);
    }

    public static Column toColumn(DataSourceColumn column) {
        Objects.requireNonNull(column, "column must not be null");
        LogicalType dataType = toLogicalType(column);
        Integer length = supportsLength(dataType.getTypeRoot()) ? column.size() : null;
        return new Column(column.name(), dataType, column.nullable(), length);
    }

    static LogicalType toLogicalType(DataSourceColumn column) {
        return switch (column.jdbcType()) {
            case Types.BOOLEAN, Types.BIT -> LogicalTypes.BOOLEAN;
            case Types.TINYINT -> LogicalTypes.TINYINT;
            case Types.SMALLINT -> LogicalTypes.SMALLINT;
            case Types.INTEGER -> LogicalTypes.INTEGER;
            case Types.BIGINT -> LogicalTypes.BIGINT;
            case Types.REAL, Types.FLOAT -> LogicalTypes.FLOAT;
            case Types.DOUBLE -> LogicalTypes.DOUBLE;
            case Types.NUMERIC, Types.DECIMAL ->
                LogicalTypes.decimal(knownPrecision(column.size()), knownScale(column.scale()));
            case Types.CHAR, Types.NCHAR -> lengthType(column.size(), true, true);
            case Types.VARCHAR,
                    Types.LONGVARCHAR,
                    Types.NVARCHAR,
                    Types.LONGNVARCHAR,
                    Types.CLOB,
                    Types.NCLOB -> LogicalTypes.STRING;
            case Types.BINARY -> lengthType(column.size(), true, false);
            case Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> LogicalTypes.BINARY;
            case Types.DATE -> LogicalTypes.DATE;
            case Types.TIME -> LogicalTypes.time(temporalPrecision(column.scale()));
            case Types.TIMESTAMP -> LogicalTypes.timestamp(temporalPrecision(column.scale()));
            case Types.TIMESTAMP_WITH_TIMEZONE -> LogicalTypes.zonedTimestamp(temporalPrecision(column.scale()));
            default ->
                throw new IllegalArgumentException(
                        "暂不支持 JDBC 字段类型：" + column.typeName() + " (" + column.jdbcType() + ")");
        };
    }

    private static LogicalType lengthType(Integer length, boolean fixed, boolean characters) {
        if (length == null || length <= 0) {
            return characters ? LogicalTypes.STRING : LogicalTypes.BINARY;
        }
        return characters ? LogicalTypes.charType(length) : LogicalTypes.fixedBinary(length);
    }

    private static int primaryKeyOrder(DataSourceColumn column) {
        Integer position = column.primaryKeyPosition();
        return position == null || position <= 0 ? Integer.MAX_VALUE : position;
    }

    private static boolean supportsLength(LogicalTypeRoot root) {
        return root == LogicalTypeRoot.CHAR
                || root == LogicalTypeRoot.VARCHAR
                || root == LogicalTypeRoot.BINARY
                || root == LogicalTypeRoot.VARBINARY;
    }

    private static Integer knownPrecision(Integer precision) {
        return precision != null && precision > 0 ? precision : null;
    }

    private static Integer knownScale(Integer scale) {
        return scale != null && scale >= 0 ? scale : null;
    }

    private static int temporalPrecision(Integer scale) {
        if (scale == null || scale < 0) {
            return 6;
        }
        if (scale > 9) {
            throw new IllegalArgumentException("JDBC temporal precision exceeds nanosecond resolution");
        }
        return scale;
    }
}
