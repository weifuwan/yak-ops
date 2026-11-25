package io.yak.ops.modeling.ddl.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DorisDdlRequest;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import java.sql.Connection;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DorisDdlEngineTest {

    @Mock private Connection connection;
    @Mock private DataSourceCatalog catalog;

    private DorisDdlEngine engine;

    @BeforeEach
    void setUp() {
        DorisConnectorAdapter connectorAdapter = new DorisConnectorAdapter();
        DorisDdlValidator validator = new DorisDdlValidator(connectorAdapter);
        DorisDdlGenerator generator = new DorisDdlGenerator(connectorAdapter, new DorisDdlFormatter());
        DorisSchemaInspector inspector = new DorisSchemaInspector();
        DorisSchemaPlanner planner = new DorisSchemaPlanner(inspector, generator);
        engine = new DorisDdlEngine(
                validator, planner, new DorisDdlExecutor(), inspector, generator);
    }

    @Test
    void shouldGenerateCreateDdlForDuplicateTable() {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(null);

        DdlPlan plan = engine.plan(connection, catalog, request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(column(
                        "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true))));

        assertTrue(plan.getDdl().contains("CREATE TABLE `warehouse`.`dwd_order_detail`"));
        assertTrue(plan.getDdl().contains("DISTRIBUTED BY HASH(`customer_id`)"));
        assertTrue(plan.getDdl().contains("(\n    `customer_id`"));
        assertTrue(!plan.isNoChanges());
    }

    @Test
    void shouldGenerateUniqueKeyDdlFromPrimaryFlag() {
        DdlColumnDefinition primaryId = new DdlColumnDefinition(
                "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, null,
                Boolean.FALSE, true, null, null);

        when(catalog.getObject(any(Connection.class), any())).thenReturn(null);
        DdlPlan plan = engine.plan(connection, catalog, request(
                DorisDataModelEnum.UNIQUE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Arrays.asList(primaryId, column(
                        "customer_name", DimensionDataTypeEnum.VARCHAR, 128, null, null, true))));

        assertTrue(plan.getDdl().contains("UNIQUE KEY(`customer_id`)"));
        assertTrue(plan.getDdl().contains("`customer_id` BIGINT NOT NULL COMMENT"));
        assertTrue(plan.getDdl().contains("`customer_name` VARCHAR(384) NULL COMMENT"));
        assertTrue(plan.getDdl().contains(",\n    `customer_name`"));
    }

    @Test
    void shouldRejectUniqueTableWithoutPrimaryKey() {
        DorisDdlRequest request = request(
                DorisDataModelEnum.UNIQUE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(column(
                        "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> engine.plan(connection, catalog, request));
        assertTrue(error.getMessage().contains("至少需要一个主键字段"));
    }

    @Test
    void shouldGenerateNotNullForDuplicateValueFields() {
        DdlColumnDefinition keyField = new DdlColumnDefinition(
                "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, null,
                Boolean.FALSE, false, null, "customer id");
        DdlColumnDefinition valueField = new DdlColumnDefinition(
                "customer_name", DimensionDataTypeEnum.VARCHAR, 128, null, null, null,
                Boolean.FALSE, false, null, "customer name, (display)");

        when(catalog.getObject(any(Connection.class), any())).thenReturn(null);
        DdlPlan plan = engine.plan(connection, catalog, request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Arrays.asList(keyField, valueField)));

        assertTrue(plan.getDdl().contains("`customer_id` BIGINT NOT NULL COMMENT"));
        assertTrue(plan.getDdl().contains(
                "`customer_name` VARCHAR(384) NOT NULL COMMENT 'customer name, (display)'"));
    }

    @Test
    void shouldRejectInvalidDecimalScale() {
        DdlColumnDefinition decimal = new DdlColumnDefinition(
                "amount", DimensionDataTypeEnum.DECIMAL, null, 10, 11, null,
                Boolean.TRUE, false, null, null);
        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(decimal));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> engine.plan(connection, catalog, request));
        assertTrue(error.getMessage().contains("小数位数"));
    }

    @Test
    void shouldTreatNullabilityChangesAsIncompatibleSchemaChanges() {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Arrays.asList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, false, 1, false, null),
                new DataSourceCatalogColumn(
                        "customer_name", "VARCHAR", Types.VARCHAR, 384, 0, true, 2, false, null)));

        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                DorisDataModelEnum.DUPLICATE,
                Collections.singletonList("customer_id"),
                Arrays.asList(
                        column("customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, false),
                        column("customer_name", DimensionDataTypeEnum.VARCHAR, 128, null, null, false)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> engine.plan(connection, catalog, request));
        assertTrue(error.getMessage().contains("字段结构发生不兼容变化"));
    }

    @Test
    void shouldPreserveNotNullWhenGeneratingAddColumnDdl() {
        DorisDdlGenerator generator = new DorisDdlGenerator(
                new DorisConnectorAdapter(), new DorisDdlFormatter());
        DdlColumnDefinition column = new DdlColumnDefinition(
                "customer_name", DimensionDataTypeEnum.VARCHAR, 128, null, null, null,
                Boolean.FALSE, false, null, "customer name");

        String ddl = generator.generateAddColumnDdl("warehouse", "dwd_order_detail", column);

        assertTrue(ddl.contains("VARCHAR(384) NOT NULL"));
        assertTrue(ddl.startsWith("ALTER TABLE `warehouse`.`dwd_order_detail`\n    ADD COLUMN"));
    }

    @Test
    void shouldRejectExistingTableWithoutSuccessfulYakOpsPublishHistory() {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());

        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(column(
                        "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> engine.plan(connection, catalog, request));
        assertTrue(error.getMessage().contains("没有 Yak-OPS 成功发布记录"));
    }

    @Test
    void shouldPlanOnlyNullableColumnAdditions() {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Collections.singletonList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, true, 1, false, null)));

        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                DorisDataModelEnum.DUPLICATE,
                Collections.singletonList("customer_id"),
                Arrays.asList(
                        column("customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true),
                        column("customer_name", DimensionDataTypeEnum.VARCHAR, 128, null, null, true)));

        DdlPlan plan = engine.plan(connection, catalog, request);

        assertEquals(1, plan.getStatements().size());
        assertTrue(plan.getStatements().get(0).startsWith(
                "ALTER TABLE `warehouse`.`dwd_order_detail`\n    ADD COLUMN `customer_name` VARCHAR(384) NULL"));
        assertTrue(!plan.isNoChanges());
    }

    @Test
    void shouldReturnNoOpPlanWhenSchemaAlreadyMatches() {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Arrays.asList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, true, 1, false, null),
                new DataSourceCatalogColumn(
                        "customer_name", "VARCHAR", Types.VARCHAR, 384, 0, true, 2, false, null)));

        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                DorisDataModelEnum.DUPLICATE,
                Collections.singletonList("customer_id"),
                Arrays.asList(
                        column("customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true),
                        column("customer_name", DimensionDataTypeEnum.VARCHAR, 128, null, null, true)));

        DdlPlan plan = engine.plan(connection, catalog, request);

        assertTrue(plan.isNoChanges());
        assertTrue(plan.getStatements().isEmpty());
    }

    private DorisDdlRequest request(
            DorisDataModelEnum model,
            DorisPublishModeEnum mode,
            DorisDataModelEnum previousModel,
            java.util.List<String> previousKeys,
            java.util.List<DdlColumnDefinition> columns) {
        return new DorisDdlRequest(
                new DdlTableDefinition("warehouse", "dwd_order_detail", "订单明细", columns),
                model, mode, previousModel, previousKeys);
    }

    private DdlColumnDefinition column(
            String name, DimensionDataTypeEnum type, Integer length,
            Integer precision, Integer scale, boolean nullable) {
        return new DdlColumnDefinition(
                name, type, length, precision, scale, null,
                nullable, false, null, name);
    }

    private CatalogObject tableObject() {
        return new CatalogObject(
                "dwd_order_detail", CatalogObjectType.TABLE, null, null, true, Collections.<String>emptyList());
    }
}
