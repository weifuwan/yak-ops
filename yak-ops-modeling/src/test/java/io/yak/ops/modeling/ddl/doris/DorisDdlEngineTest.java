package io.yak.ops.modeling.ddl.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.sql.ResultSet;
import java.sql.Statement;
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
        DorisSchemaPlanner planner = new DorisSchemaPlanner(
                inspector, generator, new DorisBackendInspector());
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
        assertTrue(plan.getDdl().contains("\"replication_num\" = \"1\""));
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
    void shouldUseRelaxedVerificationForDropRecreateAndStrictVerificationForIncremental() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Collections.singletonList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, false, 1, false, null)));

        DdlTableDefinition table = new DdlTableDefinition(
                "warehouse",
                "dwd_order_detail",
                "订单明细",
                Collections.singletonList(column(
                        "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true)));
        DorisSchemaInspector inspector = new DorisSchemaInspector();

        DorisSchemaInspector.VerificationResult recreateResult = inspector.verify(
                connection, catalog, table, DorisDataModelEnum.DUPLICATE,
                Collections.singletonList("customer_id"),
                new DorisDdlGenerator(new DorisConnectorAdapter(), new DorisDdlFormatter()),
                false);
        DorisSchemaInspector.VerificationResult incrementalResult = inspector.verify(
                connection, catalog, table, DorisDataModelEnum.DUPLICATE,
                Collections.singletonList("customer_id"),
                new DorisDdlGenerator(new DorisConnectorAdapter(), new DorisDdlFormatter()),
                true);

        assertTrue(recreateResult.isMatched());
        assertFalse(incrementalResult.isMatched());
        assertTrue(incrementalResult.getSummary().contains("NULL 属性不一致"));
        assertTrue(recreateResult.getPhysicalSchema().contains("DUPLICATE KEY"));
    }

    @Test
    void shouldTreatNullabilityChangesAsIncompatibleSchemaChanges() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
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
    void shouldUseLivePhysicalSchemaAsBaselineForManuallyCreatedTable() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Collections.singletonList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, true, 1, false, null)));

        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(column(
                        "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true)));

        DdlPlan plan = engine.plan(connection, catalog, request);

        assertTrue(plan.isNoChanges());
        assertTrue(plan.isTargetTableExists());
        assertTrue(plan.getPhysicalSchema().contains("DUPLICATE KEY"));
    }

    @Test
    void shouldProtectPartitionColumnsFromIncrementalDrop() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTableDdl(
                "CREATE TABLE `warehouse`.`dwd_order_detail` ("
                        + "`customer_id` BIGINT NOT NULL, `event_date` DATE NOT NULL"
                        + ") DUPLICATE KEY(`customer_id`) "
                        + "PARTITION BY RANGE(`event_date`) "
                        + "(PARTITION p202601 VALUES LESS THAN (\"2026-02-01\")) "
                        + "DISTRIBUTED BY HASH(`customer_id`) BUCKETS 1 "
                        + "PROPERTIES (\"replication_num\" = \"1\")");
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Arrays.asList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null),
                new DataSourceCatalogColumn(
                        "event_date", "DATE", Types.DATE, null, null, false, 2, false, null)));

        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(column(
                        "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> engine.plan(connection, catalog, request));

        assertTrue(error.getMessage().contains("分区或分桶字段"));
    }

    @Test
    void shouldMarkDriftWhenLivePhysicalSchemaDiffersFromLastSuccessfulSnapshot() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Collections.singletonList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, true, 1, false, null)));

        DorisDdlRequest request = new DorisDdlRequest(
                new DdlTableDefinition("warehouse", "dwd_order_detail", "订单明细",
                        Collections.singletonList(column(
                                "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true))),
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                DorisDataModelEnum.DUPLICATE,
                Collections.singletonList("customer_id"),
                1,
                "CREATE TABLE old_schema (...) PROPERTIES (\"replication_num\" = \"3\")");

        DdlPlan plan = engine.plan(connection, catalog, request);

        assertTrue(plan.isNoChanges());
        assertTrue(plan.isSchemaDrift());
    }

    @Test
    void shouldRejectPhysicalTableModelMismatch() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Collections.singletonList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null)));

        DdlColumnDefinition primaryId = new DdlColumnDefinition(
                "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, null,
                Boolean.FALSE, true, null, null);
        DorisDdlRequest request = request(
                DorisDataModelEnum.UNIQUE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(primaryId));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> engine.plan(connection, catalog, request));

        assertTrue(error.getMessage().contains("与当前选择的 UNIQUE 不一致"));
    }

    @Test
    void shouldPlanOnlyNullableColumnAdditions() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
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
    void shouldPlanDropForOrdinaryPhysicalColumnAndRequireConfirmation() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
        when(catalog.listColumns(any(Connection.class), any())).thenReturn(Arrays.asList(
                new DataSourceCatalogColumn(
                        "customer_id", "BIGINT", Types.BIGINT, 19, 0, true, 1, false, null),
                new DataSourceCatalogColumn(
                        "legacy_note", "VARCHAR", Types.VARCHAR, 128, 0, true, 2, false, null)));

        DorisDdlRequest request = request(
                DorisDataModelEnum.DUPLICATE,
                DorisPublishModeEnum.INCREMENTAL,
                null,
                Collections.<String>emptyList(),
                Collections.singletonList(column(
                        "customer_id", DimensionDataTypeEnum.BIGINT, null, null, null, true)));

        DdlPlan plan = engine.plan(connection, catalog, request);

        assertEquals(1, plan.getStatements().size());
        assertTrue(plan.getStatements().get(0).contains("DROP COLUMN `legacy_note`"));
        assertTrue(plan.isRequiresConfirmation());
        assertEquals(Collections.singletonList("legacy_note"), plan.getDroppedColumns());
    }

    @Test
    void shouldReturnNoOpPlanWhenSchemaAlreadyMatches() throws Exception {
        when(catalog.getObject(any(Connection.class), any())).thenReturn(tableObject());
        mockShowCreateTable("DUPLICATE");
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

    private void mockShowCreateTable(String model) throws Exception {
        mockShowCreateTableDdl(
                "CREATE TABLE `warehouse`.`dwd_order_detail` ("
                        + "`customer_id` BIGINT NOT NULL"
                        + ") " + model + " KEY(`customer_id`) "
                        + "DISTRIBUTED BY HASH(`customer_id`) BUCKETS 1 "
                        + "PROPERTIES (\"replication_num\" = \"1\")");
    }

    private void mockShowCreateTableDdl(String ddl) throws Exception {
        Statement statement = org.mockito.Mockito.mock(Statement.class);
        ResultSet result = org.mockito.Mockito.mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW CREATE TABLE `warehouse`.`dwd_order_detail`"))
                .thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(2)).thenReturn(ddl);
    }

    private DorisDdlRequest request(
            DorisDataModelEnum model,
            DorisPublishModeEnum mode,
            DorisDataModelEnum previousModel,
            java.util.List<String> previousKeys,
            java.util.List<DdlColumnDefinition> columns) {
        return new DorisDdlRequest(
                new DdlTableDefinition("warehouse", "dwd_order_detail", "订单明细", columns),
                model, mode, previousModel, previousKeys, 1);
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
