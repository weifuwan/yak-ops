package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;

/** Converts the engine's physical value types at the upstream SQL expression boundary. */
final class FlinkSchemaConverter {
    private FlinkSchemaConverter() {}

    static org.apache.flink.cdc.common.event.TableId tableId(TableId table) {
        if (table.catalog() != null && !table.catalog().isBlank()) {
            if (table.schema() != null && !table.schema().isBlank()) {
                return org.apache.flink.cdc.common.event.TableId.tableId(
                        table.catalog(), table.schema(), table.table());
            }
            return org.apache.flink.cdc.common.event.TableId.tableId(table.catalog(), table.table());
        }
        return table.schema() == null || table.schema().isBlank()
                ? org.apache.flink.cdc.common.event.TableId.tableId(table.table())
                : org.apache.flink.cdc.common.event.TableId.tableId(table.schema(), table.table());
    }

    static org.apache.flink.cdc.common.schema.Schema schema(Schema schema) {
        org.apache.flink.cdc.common.schema.Schema.Builder builder =
                org.apache.flink.cdc.common.schema.Schema.newBuilder();
        for (io.yak.ops.core.schema.PhysicalColumn column : schema.columns()) {
            builder.physicalColumn(column.name(), type(column.type()), column.comment());
        }
        return builder.primaryKey(schema.primaryKeys())
                .comment(schema.comment())
                .build();
    }

    static DataType type(LogicalType type) {
        DataType converted =
                switch (type.getTypeRoot()) {
                    case BOOLEAN -> DataTypes.BOOLEAN();
                    case TINYINT -> DataTypes.TINYINT();
                    case SMALLINT -> DataTypes.SMALLINT();
                    case INTEGER -> DataTypes.INT();
                    case BIGINT -> DataTypes.BIGINT();
                    case FLOAT -> DataTypes.FLOAT();
                    case DOUBLE -> DataTypes.DOUBLE();
                    case DATE -> DataTypes.DATE();
                    case DECIMAL -> {
                        if (!(type instanceof io.yak.ops.core.types.DecimalType decimal)) {
                            throw new IllegalArgumentException("Transform requires a resolved decimal type");
                        }
                        yield DataTypes.DECIMAL(decimal.precision(), decimal.scale());
                    }
                    case CHAR -> DataTypes.CHAR(((io.yak.ops.core.types.CharType) type).length());
                    case VARCHAR -> DataTypes.VARCHAR(((io.yak.ops.core.types.VarCharType) type).length());
                    case BINARY -> DataTypes.BINARY(((io.yak.ops.core.types.BinaryType) type).length());
                    case VARBINARY -> DataTypes.VARBINARY(((io.yak.ops.core.types.VarBinaryType) type).length());
                    case TIME_WITHOUT_TIME_ZONE -> DataTypes.TIME(((io.yak.ops.core.types.TimeType) type).precision());
                    case TIMESTAMP_WITHOUT_TIME_ZONE ->
                        DataTypes.TIMESTAMP(((io.yak.ops.core.types.TimestampType) type).precision());
                    case TIMESTAMP_WITH_TIME_ZONE ->
                        DataTypes.TIMESTAMP_TZ(((io.yak.ops.core.types.ZonedTimestampType) type).precision());
                };
        return converted.copy(type.isNullable());
    }

    static LogicalType type(DataType type) {
        LogicalType converted =
                switch (type.getTypeRoot()) {
                    case BOOLEAN -> LogicalTypes.BOOLEAN;
                    case TINYINT -> LogicalTypes.TINYINT;
                    case SMALLINT -> LogicalTypes.SMALLINT;
                    case INTEGER -> LogicalTypes.INTEGER;
                    case BIGINT -> LogicalTypes.BIGINT;
                    case FLOAT -> LogicalTypes.FLOAT;
                    case DOUBLE -> LogicalTypes.DOUBLE;
                    case DATE -> LogicalTypes.DATE;
                    case DECIMAL -> {
                        org.apache.flink.cdc.common.types.DecimalType decimal =
                                (org.apache.flink.cdc.common.types.DecimalType) type;
                        yield LogicalTypes.decimal(decimal.getPrecision(), decimal.getScale());
                    }
                    case CHAR -> LogicalTypes.charType(((org.apache.flink.cdc.common.types.CharType) type).getLength());
                    case VARCHAR ->
                        LogicalTypes.varchar(((org.apache.flink.cdc.common.types.VarCharType) type).getLength());
                    case BINARY ->
                        LogicalTypes.fixedBinary(((org.apache.flink.cdc.common.types.BinaryType) type).getLength());
                    case VARBINARY ->
                        LogicalTypes.varbinary(((org.apache.flink.cdc.common.types.VarBinaryType) type).getLength());
                    case TIME_WITHOUT_TIME_ZONE ->
                        LogicalTypes.time(((org.apache.flink.cdc.common.types.TimeType) type).getPrecision());
                    case TIMESTAMP_WITHOUT_TIME_ZONE ->
                        LogicalTypes.timestamp(((org.apache.flink.cdc.common.types.TimestampType) type).getPrecision());
                    case TIMESTAMP_WITH_TIME_ZONE ->
                        LogicalTypes.zonedTimestamp(
                                ((org.apache.flink.cdc.common.types.ZonedTimestampType) type).getPrecision());
                    case TIMESTAMP_WITH_LOCAL_TIME_ZONE ->
                        LogicalTypes.zonedTimestamp(
                                ((org.apache.flink.cdc.common.types.LocalZonedTimestampType) type).getPrecision());
                    default ->
                        throw new UnsupportedOperationException(
                                "Unsupported transformed physical type: " + type.getTypeRoot());
                };
        return converted.copy(type.isNullable());
    }
}
