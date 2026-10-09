package io.yak.ops.flow.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.flow.api.row.YakDecimalType;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcSchemaMapperTest {

    @Test
    void shouldMapCatalogColumnsInOrdinalOrder() {
        YakTableSchema schema = JdbcSchemaMapper.fromColumns(List.of(
                new DataSourceColumn("name", "VARCHAR", Types.VARCHAR, 100, null, true, 2, false, null),
                new DataSourceColumn("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null),
                new DataSourceColumn("amount", "DECIMAL", Types.DECIMAL, 10, 2, true, 3, false, null)));

        assertEquals(List.of("id", "name", "amount"), schema.columns().stream().map(column -> column.name()).toList());
        assertEquals(List.of(YakTypes.BIGINT, YakTypes.STRING, YakTypes.decimal(10, 2)), schema.columns().stream()
                .map(column -> column.dataType())
                .toList());
        assertEquals(List.of("id"), schema.primaryKeys());
        assertEquals(100, schema.column(1).length());
        YakDecimalType amountType = (YakDecimalType) schema.column(2).dataType();
        assertEquals(10, amountType.precision());
        assertEquals(2, amountType.scale());
    }

    @Test
    void shouldPreserveCompositePrimaryKeySequenceFromCatalog() {
        YakTableSchema schema = JdbcSchemaMapper.fromColumns(List.of(
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

        assertThrows(IllegalArgumentException.class, () -> JdbcSchemaMapper.toYakColumn(column));
    }
}
