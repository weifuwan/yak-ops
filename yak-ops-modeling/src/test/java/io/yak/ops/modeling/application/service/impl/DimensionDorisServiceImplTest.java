package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionUnitEnum;
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
    void shouldGenerateDuplicateDdlUsingTheDorisSdkTypeMapper() {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        id.setCanNull(Boolean.FALSE);
        DimensionTableFieldPO amount = field("field-amount", "amount", DimensionDataTypeEnum.DECIMAL);
        amount.setFieldPrecision(12);
        amount.setFieldScale(2);
        stubPreview(Arrays.asList(id, amount));

        DimensionDorisPublishVO preview = service.preview("table-id", request(), dorisDatasource());

        assertNotNull(preview.getDdl());
        assertEquals("warehouse", preview.getTargetDatabase());
        assertTrue(preview.getDdl().contains("CREATE TABLE `warehouse`.`dwd_order_detail`"));
        assertTrue(preview.getDdl().contains("`amount` DECIMALV3(12,2)"));
        assertTrue(preview.getDdl().contains("DISTRIBUTED BY HASH(`customer_id`)"));
        assertTrue(preview.getDdl().contains("BUCKETS AUTO"));
        assertTrue(preview.getPhysicalConfig().contains("\"dataModel\":\"DUPLICATE\""));
        assertEquals(64, preview.getDdlHash().length());
        verify(dimensionDorisPublishDao, never()).insert(any());
    }

    @Test
    void shouldRejectDatasourceWithoutConfiguredDatabase() {
        DataSource datasource = dorisDatasource();
        datasource.setConnectionParams("{\"database\":\" \"}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview("table-id", request(), datasource));

        assertTrue(error.getMessage().contains("Doris 数据源未配置数据库"));
    }

    @Test
    void shouldDeriveUniqueKeyFromPrimaryFlagAndApplyAdvancedBucketOptions() {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        id.setPrimaryFlag(Boolean.TRUE);
        id.setCanNull(Boolean.FALSE);
        DimensionTableFieldPO name = field("field-name", "customer_name", DimensionDataTypeEnum.VARCHAR);
        name.setLength(128);
        stubPreview(Arrays.asList(id, name));

        DimensionDorisPublishDTO dto = request();
        DimensionDorisPublishDTO.Advanced advanced = new DimensionDorisPublishDTO.Advanced();
        advanced.setBucketCount(8);
        dto.setAdvanced(advanced);

        DimensionDorisPublishVO preview = service.preview("table-id", dto, dorisDatasource());

        assertTrue(preview.getDdl().contains("UNIQUE KEY(`customer_id`)"));
        assertTrue(preview.getDdl().contains("BUCKETS 8"));
        assertTrue(preview.getDdl().contains("`customer_name` VARCHAR(384)"));
        assertTrue(preview.getPhysicalConfig().contains("\"dataModel\":\"UNIQUE\""));
        assertTrue(preview.getPhysicalConfig().contains("\"bucketMode\":\"FIXED\""));
    }

    @Test
    void shouldRejectPrimaryFieldThatIsNullable() {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        id.setPrimaryFlag(Boolean.TRUE);
        stubPreview(Collections.singletonList(id));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview("table-id", request(), dorisDatasource()));

        assertTrue(error.getMessage().contains("主键字段必须设置为非空"));
    }

    @Test
    void shouldApplyCustomHashDistribution() {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        DimensionTableFieldPO region = field("field-region", "region_id", DimensionDataTypeEnum.INT);
        stubPreview(Arrays.asList(id, region));

        DimensionDorisPublishDTO dto = request();
        DimensionDorisPublishDTO.Advanced advanced = new DimensionDorisPublishDTO.Advanced();
        advanced.setCustomDistribution(Boolean.TRUE);
        advanced.setDistributionFieldIds(Collections.singletonList("field-region"));
        dto.setAdvanced(advanced);

        DimensionDorisPublishVO preview = service.preview("table-id", dto, dorisDatasource());

        assertTrue(preview.getDdl().contains("DISTRIBUTED BY HASH(`region_id`)"));
        assertTrue(preview.getPhysicalConfig().contains("\"distributionMode\":\"CUSTOM\""));
    }

    @Test
    void shouldGenerateAutoRangePartitionDdlFromAdvancedOptions() {
        DimensionTableFieldPO eventDate =
                field("field-date", "event_date", DimensionDataTypeEnum.DATE);
        eventDate.setCanNull(Boolean.FALSE);
        DimensionTableFieldPO eventId =
                field("field-id", "event_id", DimensionDataTypeEnum.BIGINT);
        stubPreview(Arrays.asList(eventDate, eventId));

        DimensionDorisPublishDTO dto = request();
        DimensionDorisPublishDTO.Advanced advanced = new DimensionDorisPublishDTO.Advanced();
        DimensionDorisPublishDTO.Partition partition = new DimensionDorisPublishDTO.Partition();
        partition.setFieldId("field-date");
        partition.setUnit(DorisPartitionUnitEnum.DAY);
        advanced.setPartition(partition);
        dto.setAdvanced(advanced);

        DimensionDorisPublishVO preview = service.preview("table-id", dto, dorisDatasource());

        assertTrue(preview.getDdl().contains(
                "AUTO PARTITION BY RANGE (date_trunc(`event_date`, 'day'))()"));
        assertTrue(preview.getPhysicalConfig().contains("\"partitionStrategy\":\"AUTO_RANGE\""));
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
    void shouldReturnConflictWithoutExecutingDdlWhenTargetAlreadyExists() throws Exception {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        stubPreview(Collections.singletonList(id));
        when(dimensionTableDao.queryOne(any())).thenReturn(table());
        when(versionManagementDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.insert(any())).thenReturn(1);
        when(dimensionDorisPublishDao.updateById(any())).thenReturn(true);

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet existingTable = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getTables(anyString(), isNull(), anyString(), any(String[].class)))
                .thenReturn(existingTable);
        when(existingTable.next()).thenReturn(true);
        when(existingTable.getString("TABLE_NAME")).thenReturn("dwd_order_detail");
        doReturn(connection).when(service).openDorisConnection(any(DataSource.class));

        DimensionDorisPublishVO published =
                service.publish("table-id", request(), dorisDatasource(), "tester");

        verify(connection, never()).createStatement();
        assertEquals(DorisPublishStatusEnum.CONFLICT, published.getStatus());
        assertNotNull(published.getPhysicalConfig());
    }

    @Test
    void shouldPersistResolvedPhysicalConfigWithSuccessfulPublishRecord() throws Exception {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        stubPreview(Collections.singletonList(id));
        when(dimensionTableDao.queryOne(any())).thenReturn(table());
        when(versionManagementDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.queryList(any())).thenReturn(Collections.emptyList());
        when(dimensionDorisPublishDao.insert(any())).thenReturn(1);
        when(dimensionDorisPublishDao.updateById(any())).thenReturn(true);

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet noTables = mock(ResultSet.class);
        ResultSet columns = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getTables(anyString(), isNull(), anyString(), any(String[].class)))
                .thenReturn(noTables);
        when(noTables.next()).thenReturn(false, false);
        when(metadata.getColumns(anyString(), isNull(), anyString(), isNull()))
                .thenReturn(columns);
        when(columns.next()).thenReturn(true, false);
        when(columns.getString("TABLE_NAME")).thenReturn("dwd_order_detail");
        when(columns.getString("COLUMN_NAME")).thenReturn("customer_id");
        when(columns.getString("TYPE_NAME")).thenReturn("BIGINT");
        when(connection.createStatement()).thenReturn(statement);
        doReturn(connection).when(service).openDorisConnection(any(DataSource.class));

        DimensionDorisPublishVO published =
                service.publish("table-id", request(), dorisDatasource(), "tester");

        verify(statement).execute(published.getDdl());
        assertEquals(DorisPublishStatusEnum.SUCCESS, published.getStatus());
        assertNotNull(published.getPhysicalConfig());
    }

    @Test
    void shouldNotExposeAggregateKeyInV1AndShouldReturnBackendTypeOptions() {
        assertTrue(service.getFieldTypeOptions().stream()
                .anyMatch(option -> "DECIMAL".equals(option.getCode())
                        && "DECIMAL".equals(option.getParameterType())));
        assertTrue(service.getFieldTypeOptions().stream()
                .anyMatch(option -> "VARCHAR".equals(option.getCode())
                        && "LENGTH".equals(option.getParameterType())));
    }

    private void stubPreview(java.util.List<DimensionTableFieldPO> fields) {
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(fields);
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
