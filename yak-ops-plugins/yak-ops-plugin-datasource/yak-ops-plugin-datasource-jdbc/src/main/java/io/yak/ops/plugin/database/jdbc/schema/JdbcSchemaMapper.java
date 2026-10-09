package io.yak.ops.plugin.database.jdbc.schema;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.core.types.TypeKind;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 把 Datasource Catalog 的 JDBC 字段元数据转换为 YakFlow 逻辑表结构。
 *
 * @author weifuwan
 * @since 2026-09-27
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
        Integer length = isLengthType(dataType.kind()) ? column.size() : null;
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
            case Types.CHAR,
                    Types.VARCHAR,
                    Types.LONGVARCHAR,
                    Types.NCHAR,
                    Types.NVARCHAR,
                    Types.LONGNVARCHAR,
                    Types.CLOB,
                    Types.NCLOB -> LogicalTypes.STRING;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> LogicalTypes.BINARY;
            case Types.DATE -> LogicalTypes.DATE;
            case Types.TIME -> LogicalTypes.TIME;
            case Types.TIMESTAMP -> LogicalTypes.TIMESTAMP;
            case Types.TIMESTAMP_WITH_TIMEZONE -> LogicalTypes.TIMESTAMP_WITH_TIME_ZONE;
            default ->
                throw new IllegalArgumentException(
                        "暂不支持 JDBC 字段类型：" + column.typeName() + " (" + column.jdbcType() + ")");
        };
    }

    private static int primaryKeyOrder(DataSourceColumn column) {
        Integer position = column.primaryKeyPosition();
        return position == null || position <= 0 ? Integer.MAX_VALUE : position;
    }

    private static boolean isLengthType(TypeKind kind) {
        return kind == TypeKind.STRING || kind == TypeKind.BINARY;
    }

    private static Integer knownPrecision(Integer precision) {
        return precision != null && precision > 0 ? precision : null;
    }

    private static Integer knownScale(Integer scale) {
        return scale != null && scale >= 0 ? scale : null;
    }
}
