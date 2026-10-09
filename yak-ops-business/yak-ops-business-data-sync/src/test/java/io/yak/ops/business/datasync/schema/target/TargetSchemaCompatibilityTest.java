package io.yak.ops.business.datasync.schema.target;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.flow.api.row.YakTypes;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetSchemaCompatibilityTest {

    @Test
    void shouldBuildTargetWriteSchemaInSourceOrder() {
        LogicalTable source = logicalTable();
        List<DataSourceCatalogColumnVO> target = List.of(
                column("name", "VARCHAR", Types.VARCHAR, 200, null, true, 2, false, null),
                column("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, 1),
                column("optional_note", "VARCHAR", Types.VARCHAR, 100, null, true, 3, false, null));

        TargetSchemaCompatibilityResult result = TargetSchemaCompatibility.check(source, target);

        assertTrue(result.compatible());
        assertEquals(List.of("id", "name"), result.targetWriteSchema().columns().stream()
                .map(column -> column.name())
                .toList());
        assertEquals(List.of("id"), result.targetWriteSchema().primaryKeys());
    }

    @Test
    void shouldRejectNullableSourceForRequiredTarget() {
        LogicalTable source = logicalTable();
        List<DataSourceCatalogColumnVO> target = List.of(
                column("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, 1),
                column("name", "VARCHAR", Types.VARCHAR, 200, null, false, 2, false, null));

        TargetSchemaCompatibilityResult result = TargetSchemaCompatibility.check(source, target);

        assertFalse(result.compatible());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("name")));
    }

    @Test
    void shouldRejectUnmappedRequiredTargetColumn() {
        LogicalTable source = logicalTable();
        List<DataSourceCatalogColumnVO> target = List.of(
                column("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, 1),
                column("name", "VARCHAR", Types.VARCHAR, 200, null, true, 2, false, null),
                column("tenant_id", "BIGINT", Types.BIGINT, 19, 0, false, 3, false, null));

        TargetSchemaCompatibilityResult result = TargetSchemaCompatibility.check(source, target);

        assertFalse(result.compatible());
        assertTrue(result.issues().contains("目标表存在来源未映射的必填字段：tenant_id"));
    }

    @Test
    void shouldAllowIntegerToWiderDecimalTarget() {
        LogicalTable source = new LogicalTable(
                "orders",
                null,
                1,
                List.of(new LogicalColumn("id", YakTypes.INTEGER, false, null, null)),
                List.of("id"));
        List<DataSourceCatalogColumnVO> target = List.of(
                column("id", "DECIMAL", Types.DECIMAL, 12, 0, false, 1, true, 1));

        TargetSchemaCompatibilityResult result = TargetSchemaCompatibility.check(source, target);

        assertTrue(result.compatible());
    }

    private LogicalTable logicalTable() {
        return new LogicalTable(
                "orders",
                null,
                1,
                List.of(
                        new LogicalColumn("id", YakTypes.BIGINT, false, null, null),
                        new LogicalColumn("name", YakTypes.STRING, true, 100, null)),
                List.of("id"));
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
            Integer primaryKeyPosition) {
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
        return column;
    }
}
