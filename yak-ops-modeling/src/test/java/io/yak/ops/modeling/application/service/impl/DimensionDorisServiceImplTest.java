package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import io.yak.ops.spi.enums.DbType;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DimensionDorisServiceImplTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock private DimensionTableDao dimensionTableDao;
    @Mock private DimensionTableFieldDao dimensionTableFieldDao;
    @Mock private DimensionDorisPublishDao dimensionDorisPublishDao;
    @Mock private VersionManagementDao versionManagementDao;

    @Spy
    @InjectMocks
    private DimensionDorisServiceImpl service;

    @Test
    void shouldGenerateDuplicateDdlFromExplicitTableModel() throws Exception {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        DimensionTableFieldPO amount = field("field-amount", "amount", DimensionDataTypeEnum.DECIMAL);
        amount.setFieldPrecision(12);
        amount.setFieldScale(2);
        stubPreview(Arrays.asList(id, amount));

        DimensionDorisPublishVO preview = service.preview("table-id", request(), dorisDatasource());

        assertNotNull(preview.getDdl());
        assertEquals("warehouse", preview.getTargetDatabase());
        assertEquals(DorisDataModelEnum.DUPLICATE, preview.getTableModel());
        assertTrue(preview.getDdl().contains("CREATE TABLE `warehouse`.`dwd_order_detail`"));
        assertTrue(preview.getDdl().contains("DUPLICATE KEY(`customer_id`)"));
        assertTrue(preview.getDdl().contains("`amount` DECIMALV3(12,2)"));
        assertTrue(preview.getDdl().contains("DISTRIBUTED BY HASH(`customer_id`)"));
        assertTrue(preview.getDdl().contains("BUCKETS AUTO"));
        assertEquals(64, preview.getDdlHash().length());
        verify(dimensionDorisPublishDao, never()).insert(any());
    }

    @Test
    void shouldGenerateUniqueDdlOnlyWhenExplicitlySelected() throws Exception {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        id.setPrimaryFlag(Boolean.TRUE);
        id.setCanNull(Boolean.FALSE);
        DimensionTableFieldPO name = field("field-name", "customer_name", DimensionDataTypeEnum.VARCHAR);
        name.setLength(128);
        stubPreview(Arrays.asList(id, name));

        DimensionDorisPublishDTO dto = request();
        dto.setTableModel(DorisDataModelEnum.UNIQUE);

        DimensionDorisPublishVO preview = service.preview("table-id", dto, dorisDatasource());

        assertEquals(DorisDataModelEnum.UNIQUE, preview.getTableModel());
        assertTrue(preview.getDdl().contains("UNIQUE KEY(`customer_id`)"));
        assertTrue(preview.getDdl().contains("DISTRIBUTED BY HASH(`customer_id`)"));
        assertTrue(preview.getDdl().contains("BUCKETS AUTO"));
        assertTrue(preview.getDdl().contains("`customer_name` VARCHAR(384)"));
    }

    @Test
    void shouldRejectUniqueModelWithoutPrimaryFields() throws Exception {
        stubPreview(Collections.singletonList(
                field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT)));

        DimensionDorisPublishDTO dto = request();
        dto.setTableModel(DorisDataModelEnum.UNIQUE);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview("table-id", dto, dorisDatasource()));

        assertTrue(error.getMessage().contains("至少需要一个主键字段"));
    }

    @Test
    void shouldRejectNullableUniqueKey() throws Exception {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        id.setPrimaryFlag(Boolean.TRUE);
        id.setCanNull(Boolean.TRUE);
        stubPreview(Collections.singletonList(id));

        DimensionDorisPublishDTO dto = request();
        dto.setTableModel(DorisDataModelEnum.UNIQUE);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview("table-id", dto, dorisDatasource()));

        assertTrue(error.getMessage().contains("主键字段必须设置为非空"));
    }

    @Test
    void shouldRejectDatasourceWithoutConfiguredDatabase() {
        DataSource datasource = dorisDatasource();
        datasource.setConnectionParams("{\"database\":null}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview("table-id", request(), datasource));

        assertTrue(error.getMessage().contains("Doris 数据源未配置数据库"));
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
    void shouldRequireConfirmationForDropRecreate() {
        DimensionDorisPublishDTO dto = request();
        dto.setPublishMode(DorisPublishModeEnum.DROP_RECREATE);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publish("table-id", dto, dorisDatasource(), "tester"));

        assertTrue(error.getMessage().contains("删除重建需要二次确认"));
    }

    @Test
    void shouldRejectIncrementalPublishForExistingTableWithoutYakOpsHistory() throws Exception {
        stubPublishInfrastructure();
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        Connection connection = connectionWithExistingTable();
        doReturn(connection).when(service).openDorisConnection(any(DataSource.class));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publish("table-id", request(), dorisDatasource(), "tester"));

        assertTrue(error.getMessage().contains("没有 Yak-OPS 成功发布记录"));
        verify(dimensionDorisPublishDao, never()).insert(any());
        verify(connection, never()).createStatement();
    }

    @Test
    void shouldCreateMissingTableAndPersistPublishMetadata() throws Exception {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        stubPublishInfrastructure();
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(id));

        Connection connection = connectionWithoutTable();
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getMetaData().getColumns(anyString(), isNull(), anyString(), isNull()))
                .thenReturn(columnsFor("customer_id", "BIGINT"));
        doReturn(connection).when(service).openDorisConnection(any(DataSource.class));

        DimensionDorisPublishVO published =
                service.publish("table-id", request(), dorisDatasource(), "tester");

        verify(statement).execute(startsWith("CREATE TABLE"));
        assertEquals(DorisPublishStatusEnum.SUCCESS, published.getStatus());
        assertEquals(DorisDataModelEnum.DUPLICATE, published.getTableModel());
        assertEquals(DorisPublishModeEnum.INCREMENTAL, published.getPublishMode());

        ArgumentCaptor<DimensionDorisPublishPO> captor =
                ArgumentCaptor.forClass(DimensionDorisPublishPO.class);
        verify(dimensionDorisPublishDao).insert(captor.capture());
        assertEquals(DorisDataModelEnum.DUPLICATE, captor.getValue().getTableModel());
        assertEquals(DorisPublishModeEnum.INCREMENTAL, captor.getValue().getPublishMode());
        assertEquals("测试发布", captor.getValue().getPublishRemark());
    }

    @Test
    void shouldDropAndRecreateWhenExplicitlyConfirmed() throws Exception {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        stubPublishInfrastructure();
        when(dimensionTableFieldDao.queryList(any())).thenReturn(Collections.singletonList(id));

        Connection connection = connectionWithExistingTable();
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getMetaData().getColumns(anyString(), isNull(), anyString(), isNull()))
                .thenReturn(columnsFor("customer_id", "BIGINT"));
        doReturn(connection).when(service).openDorisConnection(any(DataSource.class));

        DimensionDorisPublishDTO dto = request();
        dto.setPublishMode(DorisPublishModeEnum.DROP_RECREATE);
        dto.setConfirmDrop(Boolean.TRUE);

        DimensionDorisPublishVO published =
                service.publish("table-id", dto, dorisDatasource(), "tester");

        verify(statement).execute("DROP TABLE IF EXISTS `warehouse`.`dwd_order_detail`");
        verify(statement).execute(startsWith("CREATE TABLE"));
        assertEquals(DorisPublishStatusEnum.SUCCESS, published.getStatus());
        assertEquals(DorisPublishModeEnum.DROP_RECREATE, published.getPublishMode());
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
    void shouldExposeBackendDorisFieldTypeOptions() {
        assertTrue(service.getFieldTypeOptions().stream()
                .anyMatch(option -> "DECIMAL".equals(option.getCode())
                        && "DECIMAL".equals(option.getParameterType())));
        assertTrue(service.getFieldTypeOptions().stream()
                .anyMatch(option -> "VARCHAR".equals(option.getCode())
                        && "LENGTH".equals(option.getParameterType())));
    }

    private void stubPreview(java.util.List<DimensionTableFieldPO> fields) throws Exception {
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(fields);
        doReturn(connectionWithoutTable()).when(service).openDorisConnection(any(DataSource.class));
    }

    private void stubPublishInfrastructure() {
        when(dimensionTableDao.queryOne(any())).thenReturn(table());
        when(versionManagementDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.insert(any())).thenReturn(1);
        when(dimensionDorisPublishDao.updateById(any())).thenReturn(true);
    }

    private Connection connectionWithoutTable() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet noTables = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getTables(anyString(), isNull(), anyString(), any(String[].class)))
                .thenReturn(noTables);
        when(noTables.next()).thenReturn(false);
        return connection;
    }

    private Connection connectionWithExistingTable() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet existingTable = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getTables(anyString(), isNull(), anyString(), any(String[].class)))
                .thenReturn(existingTable);
        when(existingTable.next()).thenReturn(true);
        when(existingTable.getString("TABLE_NAME")).thenReturn("dwd_order_detail");
        return connection;
    }

    private ResultSet columnsFor(String columnName, String typeName) throws Exception {
        ResultSet columns = mock(ResultSet.class);
        when(columns.next()).thenReturn(true, false);
        when(columns.getString("TABLE_NAME")).thenReturn("dwd_order_detail");
        when(columns.getString("COLUMN_NAME")).thenReturn(columnName);
        when(columns.getString("TYPE_NAME")).thenReturn(typeName);
        when(columns.getInt("COLUMN_SIZE")).thenReturn(0);
        when(columns.getInt("DECIMAL_DIGITS")).thenReturn(0);
        when(columns.wasNull()).thenReturn(false);
        return columns;
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

    private DimensionTableFieldPO field(String id, String name, DimensionDataTypeEnum type) {
        DimensionTableFieldPO field = new DimensionTableFieldPO();
        field.setId(id);
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
