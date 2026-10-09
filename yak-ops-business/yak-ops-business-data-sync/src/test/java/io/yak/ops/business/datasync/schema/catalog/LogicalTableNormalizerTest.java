package io.yak.ops.business.datasync.schema.catalog;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.flow.api.row.YakDecimalType;
import io.yak.ops.flow.api.row.YakTypeKind;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicalTableNormalizerTest {

    @Test
    void shouldNormalizeCatalogMetadataIntoLogicalTable() {
        DataSourceCatalogTableVO table = table("orders", " 订单表 ");

        LogicalTable logicalTable = LogicalTableNormalizer.fromCatalog(
                table,
                List.of(
                        column("name", "VARCHAR", Types.VARCHAR, 100, null, true, 2, false, null, " 名称 "),
                        column("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, 1, " 主键 "),
                        column("amount", "DECIMAL", Types.DECIMAL, 18, 2, true, 3, false, null, "金额")));

        assertEquals("orders", logicalTable.name());
        assertEquals("订单表", logicalTable.comment());
        assertEquals(1, logicalTable.schemaVersion());
        assertEquals(List.of("id", "name", "amount"), logicalTable.columns().stream()
                .map(LogicalColumn::name)
                .toList());
        assertEquals(List.of("id"), logicalTable.primaryKeys());
        assertEquals(YakTypeKind.BIGINT, logicalTable.columns().get(0).dataType().kind());
        assertEquals(YakTypeKind.STRING, logicalTable.columns().get(1).dataType().kind());
        assertEquals(100, logicalTable.columns().get(1).length());
        assertEquals("名称", logicalTable.columns().get(1).comment());

        YakDecimalType amount = (YakDecimalType) logicalTable.columns().get(2).dataType();
        assertEquals(18, amount.precision());
        assertEquals(2, amount.scale());
    }

    @Test
    void shouldPreserveCompositePrimaryKeySequence() {
        LogicalTable logicalTable = LogicalTableNormalizer.fromCatalog(
                table("order_items", null),
                List.of(
                        column("tenant_id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, 2, null),
                        column("order_id", "BIGINT", Types.BIGINT, 19, 0, false, 2, true, 1, null)));

        assertEquals(List.of("order_id", "tenant_id"), logicalTable.primaryKeys());
    }

    @Test
    void shouldRejectUnsupportedJdbcTypeThroughSharedMapper() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LogicalTableNormalizer.fromCatalog(
                        table("events", null),
                        List.of(column(
                                "payload", "OTHER", Types.OTHER, null, null, true, 1, false, null, null))));
    }

    private DataSourceCatalogTableVO table(String name, String remarks) {
        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
        table.setName(name);
        table.setType("TABLE");
        table.setRemarks(remarks);
        return table;
    }

    private DataSourceCatalogColumnVO column(
            String name,
            String typeName,
            int jdbcType,
            Integer size,
            Integer scale,
            boolean nullable,
            int ordinal,
            boolean primaryKey,
            Integer primaryKeyPosition,
            String remarks) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(typeName);
        column.setJdbcType(jdbcType);
        column.setSize(size);
        column.setScale(scale);
        column.setNullable(nullable);
        column.setOrdinalPosition(ordinal);
        column.setPrimaryKey(primaryKey);
        column.setPrimaryKeyPosition(primaryKeyPosition);
        column.setRemarks(remarks);
        return column;
    }
}
