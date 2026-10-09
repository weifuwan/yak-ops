package io.yak.ops.plugin.database.jdbc.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcSchemaMapperTest {

    @Test
    void shouldMapCatalogColumnsInOrdinalOrder() {
        TableSchema schema = JdbcSchemaMapper.fromColumns(List.of(
                new DataSourceColumn("name", "VARCHAR", Types.VARCHAR, 100, null, true, 2, false, null),
                new DataSourceColumn("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null),
                new DataSourceColumn("amount", "DECIMAL", Types.DECIMAL, 10, 2, true, 3, false, null)));

        assertEquals(List.of("id", "name", "amount"), schema.columns().stream().map(column -> column.name()).toList());
        assertEquals(List.of(LogicalTypes.BIGINT.copy(false), LogicalTypes.varchar(100), LogicalTypes.decimal(10, 2)), schema.columns().stream()
                .map(column -> column.dataType())
                .toList());
        assertEquals(List.of("id"), schema.primaryKeys());
        assertEquals(100, schema.column(1).length());
        DecimalType amountType = (DecimalType) schema.column(2).dataType();
        assertEquals(10, amountType.precision());
        assertEquals(2, amountType.scale());
    }

    @Test
    void shouldPreserveCompositePrimaryKeySequenceFromCatalog() {
        TableSchema schema = JdbcSchemaMapper.fromColumns(List.of(
                new DataSourceColumn(
                        "tenant_id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, 2, null),
                new DataSourceColumn(
                        "order_id", "BIGINT", Types.BIGINT, 19, 0, false, 2, true, 1, null),
                new DataSourceColumn(
                        "name", "VARCHAR", Types.VARCHAR, 100, null, true, 3, false, null, null)));

        assertEquals(List.of("tenant_id", "order_id", "name"), schema.columns().stream()
                .map(column -> column.name())
                .toList());
        assertEquals(List.of("order_id", "tenant_id"), schema.primaryKeys());
    }

    @Test
    void shouldRejectUnsupportedJdbcTypeBeforeCompatibility() {
        DataSourceColumn column =
                new DataSourceColumn("time_tz", "TIME_WITH_TIME_ZONE", Types.TIME_WITH_TIMEZONE, null, null, true, 1, false, null);

        assertThrows(IllegalArgumentException.class, () -> JdbcSchemaMapper.toColumn(column));
    }

    @Test
    void shouldDistinguishFixedWidthAndTimestampPrecisionFromCatalog() {
        TableSchema schema = JdbcSchemaMapper.fromColumns(List.of(
                new DataSourceColumn("code", "CHAR", Types.CHAR, 12, null, false, 1, false, null),
                new DataSourceColumn("raw", "BINARY", Types.BINARY, 16, null, true, 2, false, null),
                new DataSourceColumn("updated_at", "TIMESTAMP", Types.TIMESTAMP, null, 3, true, 3, false, null),
                new DataSourceColumn("timezone_at", "TIMESTAMPTZ", Types.TIMESTAMP_WITH_TIMEZONE, null, 6, true, 4, false, null)));

        assertEquals("CHAR(12) NOT NULL", schema.column(0).dataType().asSerializableString());
        assertEquals("BINARY(16)", schema.column(1).dataType().asSerializableString());
        assertEquals("TIMESTAMP(3)", schema.column(2).dataType().asSerializableString());
        assertEquals("TIMESTAMP(6) WITH TIME ZONE", schema.column(3).dataType().asSerializableString());
    }

    @Test
    void shouldPreserveUnresolvedAndHighPrecisionDecimalMetadataWithoutLying() {
        var missing = JdbcSchemaMapper.toColumn(
                new DataSourceColumn("amount", "NUMERIC", Types.NUMERIC, null, null, true, 1, false, null));
        var large = JdbcSchemaMapper.toColumn(
                new DataSourceColumn("amount", "NUMERIC", Types.NUMERIC, 65, 30, true, 1, false, null));

        assertEquals(false, missing.dataType().isResolved());
        assertEquals("UNRESOLVED_DECIMAL(?, ?)", missing.dataType().asSerializableString());
        assertEquals(false, large.dataType().isResolved());
        assertEquals("UNRESOLVED_DECIMAL(65, 30)", large.dataType().asSerializableString());
    }
}
