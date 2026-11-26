package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
import io.yak.ops.dao.entity.enums.DorisPublishStatusEnum;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionDorisPublishDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisPublishVO;
import io.yak.ops.modeling.ddl.doris.DorisDataSourceAdapter;
import io.yak.ops.modeling.ddl.doris.DorisDataSourceContext;
import io.yak.ops.modeling.ddl.doris.DorisDdlEngine;
import io.yak.ops.modeling.ddl.doris.DorisSchemaVerificationException;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.spi.enums.DbType;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;

@ExtendWith(MockitoExtension.class)
class DimensionDorisServiceImplTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock private DimensionTableDao dimensionTableDao;
    @Mock private DimensionTableFieldDao dimensionTableFieldDao;
    @Mock private DimensionDorisPublishDao dimensionDorisPublishDao;
    @Mock private VersionManagementDao versionManagementDao;
    @Mock private DorisDdlEngine ddlEngine;
    @Mock private DorisDataSourceAdapter dataSourceAdapter;
    @Mock private DimensionDorisPublishRecordService publishRecordService;
    @Mock private Connection connection;
    @Mock private DataSourceCatalog catalog;

    @InjectMocks
    private DimensionDorisServiceImpl service;

    @Test
    void shouldDelegatePreviewToDdlEngine() throws Exception {
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(
                field("customer_id", DimensionDataTypeEnum.BIGINT)));
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dataSourceAdapter.open(any(DataSource.class)))
                .thenReturn(new DorisDataSourceContext(connection, catalog, "warehouse"));
        when(ddlEngine.plan(any(Connection.class), any(DataSourceCatalog.class), any()))
                .thenReturn(new DdlPlan("CREATE TABLE ddl", Collections.singletonList("CREATE TABLE ddl"),
                        false, Collections.singletonList("customer_id")));

        DimensionDorisPublishVO preview =
                service.preview("table-id", request(), dorisDatasource());

        assertEquals("warehouse", preview.getTargetDatabase());
        assertEquals("dwd_order_detail", preview.getTargetTable());
        assertEquals("CREATE TABLE ddl", preview.getDdl());
        assertEquals(64, preview.getDdlHash().length());
        assertFalse(Boolean.TRUE.equals(preview.getNoChanges()));
        verify(dimensionDorisPublishDao, never()).insert(any());
    }

    @Test
    void shouldRejectPreviewForUnsubmittedModel() {
        DimensionTablePO draft = table();
        draft.setSubmit(Boolean.FALSE);
        when(dimensionTableDao.queryById("table-id")).thenReturn(draft);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview("table-id", request(), dorisDatasource()));

        assertTrue(error.getMessage().contains("请先提交维度模型"));
    }

    @Test
    void shouldRequirePublishRemark() {
        DimensionDorisPublishDTO dto = request();
        dto.setPublishRemark(" ");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publish("table-id", dto, dorisDatasource(), "tester"));

        assertTrue(error.getMessage().contains("请输入发布备注"));
    }

    @Test
    void shouldRequireConfirmationForDropRecreate() throws Exception {
        DimensionDorisPublishDTO dto = request();
        dto.setPublishMode(DorisPublishModeEnum.DROP_RECREATE);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publish("table-id", dto, dorisDatasource(), "tester"));

        assertTrue(error.getMessage().contains("删除重建需要二次确认"));
        verify(dataSourceAdapter, never()).open(any(DataSource.class));
    }

    @Test
    void shouldPersistPublishStateOutsideDdlExecution() throws Exception {
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(
                field("customer_id", DimensionDataTypeEnum.BIGINT)));
        when(versionManagementDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dataSourceAdapter.open(any(DataSource.class)))
                .thenReturn(new DorisDataSourceContext(connection, catalog, "warehouse"));
        when(ddlEngine.plan(any(Connection.class), any(DataSourceCatalog.class), any()))
                .thenReturn(new DdlPlan(
                        "CREATE TABLE ddl", Collections.singletonList("CREATE TABLE ddl"),
                        false, Collections.singletonList("customer_id")));
        when(publishRecordService.createRunning(
                eq("table-id"), any(DimensionDorisPublishPO.class), any()))
                .thenAnswer(invocation -> {
                    DimensionDorisPublishPO record = (DimensionDorisPublishPO) invocation.getArguments()[1];
                    record.setStatus(DorisPublishStatusEnum.RUNNING);
                    record.setStartedAt((java.util.Date) invocation.getArguments()[2]);
                    return record;
                });
        when(dimensionDorisPublishDao.queryById(anyString())).thenReturn(null);
        when(ddlEngine.execute(
                any(Connection.class), any(DataSourceCatalog.class), any(), any(DdlPlan.class), anyString()))
                .thenReturn("CREATE TABLE `warehouse`.`dwd_order_detail` (...)");

        DimensionDorisPublishVO published =
                service.publish("table-id", request(), dorisDatasource(), "tester");

        assertEquals(DorisPublishStatusEnum.SUCCESS, published.getStatus());
        assertEquals(DorisDataModelEnum.DUPLICATE, published.getTableModel());
        assertEquals(DorisPublishModeEnum.INCREMENTAL, published.getPublishMode());
        verify(publishRecordService).createRunning(
                eq("table-id"), any(DimensionDorisPublishPO.class), any());
        verify(publishRecordService).updatePhysicalSchema(
                anyString(), eq("CREATE TABLE `warehouse`.`dwd_order_detail` (...)"), eq("tester"));
        verify(publishRecordService).updateStatus(
                anyString(), eq(DorisPublishStatusEnum.SUCCESS), eq(null), any(), any(), any(), eq("tester"));
        verify(ddlEngine).execute(
                any(Connection.class), any(DataSourceCatalog.class), any(), any(DdlPlan.class), anyString());
    }

    @Test
    void shouldPersistVerificationFailureSeparatelyAndKeepObservedPhysicalSchema() throws Exception {
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(
                field("customer_id", DimensionDataTypeEnum.BIGINT)));
        when(versionManagementDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dataSourceAdapter.open(any(DataSource.class)))
                .thenReturn(new DorisDataSourceContext(connection, catalog, "warehouse"));
        when(ddlEngine.plan(any(Connection.class), any(DataSourceCatalog.class), any()))
                .thenReturn(new DdlPlan(
                        "CREATE TABLE ddl", Collections.singletonList("CREATE TABLE ddl"),
                        false, Collections.singletonList("customer_id")));
        when(publishRecordService.createRunning(
                eq("table-id"), any(DimensionDorisPublishPO.class), any()))
                .thenAnswer(invocation -> {
                    DimensionDorisPublishPO record = (DimensionDorisPublishPO) invocation.getArguments()[1];
                    record.setStatus(DorisPublishStatusEnum.RUNNING);
                    record.setStartedAt((java.util.Date) invocation.getArguments()[2]);
                    return record;
                });
        when(dimensionDorisPublishDao.queryById(anyString())).thenReturn(null);
        when(ddlEngine.execute(
                any(Connection.class), any(DataSourceCatalog.class), any(), any(DdlPlan.class), anyString()))
                .thenThrow(new DorisSchemaVerificationException(
                        "Doris 已执行 DROP/CREATE，但物理结构校验未通过：字段类型不一致",
                        "CREATE TABLE warehouse.dwd_order_detail (...)"));

        DimensionDorisPublishVO published =
                service.publish("table-id", request(), dorisDatasource(), "tester");

        assertEquals(DorisPublishStatusEnum.VERIFY_FAILED, published.getStatus());
        assertEquals("CREATE TABLE warehouse.dwd_order_detail (...)", published.getPhysicalSchema());
        verify(publishRecordService).updatePhysicalSchema(
                anyString(),
                eq("CREATE TABLE warehouse.dwd_order_detail (...)"),
                eq("tester"));
        verify(publishRecordService).updateStatus(
                anyString(),
                eq(DorisPublishStatusEnum.VERIFY_FAILED),
                anyString(),
                any(),
                any(),
                any(),
                eq("tester"));
    }

    @Test
    void shouldNotLoadHistoricalBaselineForDropRecreatePreview() throws Exception {
        DimensionDorisPublishDTO dto = request();
        dto.setPublishMode(DorisPublishModeEnum.DROP_RECREATE);
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(
                field("customer_id", DimensionDataTypeEnum.BIGINT)));
        when(dataSourceAdapter.open(any(DataSource.class)))
                .thenReturn(new DorisDataSourceContext(connection, catalog, "warehouse"));
        when(ddlEngine.plan(any(Connection.class), any(DataSourceCatalog.class), any()))
                .thenAnswer(invocation -> {
                    io.yak.ops.modeling.ddl.model.DorisDdlRequest ddlRequest =
                            (io.yak.ops.modeling.ddl.model.DorisDdlRequest) invocation.getArguments()[2];
                    assertEquals(null, ddlRequest.getPreviousTableModel());
                    assertTrue(ddlRequest.getPreviousKeyFields().isEmpty());
                    assertEquals(null, ddlRequest.getPreviousPhysicalSchema());
                    return new DdlPlan(
                            "DROP TABLE IF EXISTS ...; CREATE TABLE ...",
                            Arrays.asList("DROP TABLE IF EXISTS ...", "CREATE TABLE ..."),
                            false,
                            Collections.singletonList("customer_id"));
                });

        service.preview("table-id", dto, dorisDatasource());

        verify(dimensionDorisPublishDao, never()).queryList(any());
    }

    @Test
    void shouldRequireExplicitConfirmationBeforeDroppingPhysicalColumns() throws Exception {
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(
                field("customer_id", DimensionDataTypeEnum.BIGINT)));
        when(versionManagementDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dataSourceAdapter.open(any(DataSource.class)))
                .thenReturn(new DorisDataSourceContext(connection, catalog, "warehouse"));
        when(ddlEngine.plan(any(Connection.class), any(DataSourceCatalog.class), any()))
                .thenReturn(new DdlPlan(
                        "ALTER TABLE ... DROP COLUMN `legacy_note`",
                        Collections.singletonList("ALTER TABLE ... DROP COLUMN `legacy_note`"),
                        false,
                        Collections.singletonList("customer_id"),
                        null,
                        null,
                        false,
                        null,
                        true,
                        Collections.<String>emptyList(),
                        Collections.singletonList("legacy_note"),
                        true,
                        false,
                        "SHOW CREATE TABLE ..."));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publish("table-id", request(), dorisDatasource(), "tester"));

        assertTrue(error.getMessage().contains("需要二次确认"));
        verify(publishRecordService, never()).createRunning(
                eq("table-id"), any(DimensionDorisPublishPO.class), any());
    }

    @Test
    void shouldRejectStaleDdlHashForDestructiveColumnConfirmation() throws Exception {
        DimensionDorisPublishDTO dto = request();
        dto.setConfirmDropColumns(Boolean.TRUE);
        dto.setConfirmedDdlHash("stale-preview-hash");
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(
                field("customer_id", DimensionDataTypeEnum.BIGINT)));
        when(versionManagementDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dataSourceAdapter.open(any(DataSource.class)))
                .thenReturn(new DorisDataSourceContext(connection, catalog, "warehouse"));
        when(ddlEngine.plan(any(Connection.class), any(DataSourceCatalog.class), any()))
                .thenReturn(new DdlPlan(
                        "ALTER TABLE ... DROP COLUMN `legacy_note`",
                        Collections.singletonList("ALTER TABLE ... DROP COLUMN `legacy_note`"),
                        false,
                        Collections.singletonList("customer_id"),
                        null,
                        null,
                        false,
                        null,
                        true,
                        Collections.<String>emptyList(),
                        Collections.singletonList("legacy_note"),
                        true,
                        false,
                        "SHOW CREATE TABLE ..."));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publish("table-id", dto, dorisDatasource(), "tester"));

        assertTrue(error.getMessage().contains("物理表结构已变化"));
        verify(publishRecordService, never()).createRunning(
                eq("table-id"), any(DimensionDorisPublishPO.class), any());
    }

    @Test
    void shouldExposeDorisFieldTypeOptions() {
        when(ddlEngine.getFieldTypeOptions()).thenReturn(Arrays.asList(
                new io.yak.ops.modeling.ddl.model.DorisFieldTypeOption(
                        "DECIMAL", "DECIMAL", "DECIMAL", null, 38, 38)));

        assertEquals("DECIMAL", service.getFieldTypeOptions().get(0).getCode());
        assertEquals("DECIMAL", service.getFieldTypeOptions().get(0).getParameterType());
    }

    private DimensionTablePO table() {
        DimensionTablePO table = new DimensionTablePO();
        table.setId("table-id");
        table.setTableType(DimensionTableTypeEnum.DETAIL);
        table.setTableName("dwd_order_detail");
        table.setCnName("订单明细");
        table.setSubmit(Boolean.TRUE);
        return table;
    }

    private DimensionTableFieldPO field(String name, DimensionDataTypeEnum type) {
        DimensionTableFieldPO field = new DimensionTableFieldPO();
        field.setId("field-" + name);
        field.setTableId("table-id");
        field.setOrdinal(0);
        field.setFieldName(name);
        field.setCnName(name);
        field.setDataType(type);
        field.setCanNull(Boolean.TRUE);
        return field;
    }

    private DimensionDorisPublishDTO request() {
        DimensionDorisPublishDTO dto = new DimensionDorisPublishDTO();
        dto.setDatasourceId(100);
        dto.setTableModel(DorisDataModelEnum.DUPLICATE);
        dto.setPublishMode(DorisPublishModeEnum.INCREMENTAL);
        dto.setPublishRemark("测试发布");
        return dto;
    }

    private DataSource dorisDatasource() {
        DataSource datasource = new DataSource();
        datasource.setId(100);
        datasource.setName("test-doris");
        datasource.setType(DbType.DORIS);
        datasource.setConnectionParams("{\"database\":\"warehouse\"}");
        return datasource;
    }
}
