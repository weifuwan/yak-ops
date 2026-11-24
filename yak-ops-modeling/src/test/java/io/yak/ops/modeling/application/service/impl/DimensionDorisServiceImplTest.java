package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.DimensionDorisConfigPO;
import io.yak.ops.dao.entity.DimensionDorisDistributionFieldPO;
import io.yak.ops.dao.entity.DimensionDorisKeyFieldPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DorisBucketModeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisDistributionTypeEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionStrategyEnum;
import io.yak.ops.dao.repository.modeling.DimensionDorisConfigDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisDistributionFieldDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisKeyFieldDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionDorisPublishDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisPublishVO;
import io.yak.ops.spi.enums.DbType;
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
    @Mock private DimensionDorisConfigDao dimensionDorisConfigDao;
    @Mock private DimensionDorisKeyFieldDao dimensionDorisKeyFieldDao;
    @Mock private DimensionDorisDistributionFieldDao dimensionDorisDistributionFieldDao;
    @Mock private DimensionDorisPublishDao dimensionDorisPublishDao;
    @Mock private VersionManagementDao versionManagementDao;

    @InjectMocks
    private DimensionDorisServiceImpl service;

    @Test
    void shouldGenerateDuplicateDdlUsingTheDorisSdkTypeMapper() {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        DimensionTableFieldPO amount = field("field-amount", "amount", DimensionDataTypeEnum.DECIMAL);
        amount.setFieldPrecision(12);
        amount.setFieldScale(2);
        stubPreview(Arrays.asList(id, amount), null,
                Collections.emptyList(), Collections.emptyList());

        DimensionDorisPublishVO preview = service.preview("table-id", request(), dorisDatasource());

        assertNotNull(preview.getDdl());
        assertTrue(preview.getDdl().contains(
                "CREATE TABLE IF NOT EXISTS `warehouse`.`dwd_order_detail`"));
        assertTrue(preview.getDdl().contains("`amount` DECIMALV3(12,2)"));
        assertTrue(preview.getDdl().contains("DISTRIBUTED BY HASH(`customer_id`)"));
        assertTrue(preview.getDdl().contains("BUCKETS AUTO"));
        assertEquals(64, preview.getDdlHash().length());
    }

    @Test
    void shouldGenerateUniqueKeyAndFixedBucketDdlFromPersistedConfiguration() {
        DimensionTableFieldPO id = field("field-id", "customer_id", DimensionDataTypeEnum.BIGINT);
        id.setCanNull(Boolean.FALSE);
        DimensionTableFieldPO name = field("field-name", "customer_name", DimensionDataTypeEnum.VARCHAR);
        name.setLength(128);

        DimensionDorisConfigPO config = new DimensionDorisConfigPO();
        config.setDataModel(DorisDataModelEnum.UNIQUE);
        config.setPartitionStrategy(DorisPartitionStrategyEnum.NONE);
        config.setDistributionType(DorisDistributionTypeEnum.HASH);
        config.setBucketMode(DorisBucketModeEnum.FIXED);
        config.setBucketCount(8);
        config.setTableProperties("{}");

        DimensionDorisKeyFieldPO key = new DimensionDorisKeyFieldPO();
        key.setFieldId("field-id");
        key.setOrdinal(0);
        DimensionDorisDistributionFieldPO distribution = new DimensionDorisDistributionFieldPO();
        distribution.setFieldId("field-id");
        distribution.setOrdinal(0);
        stubPreview(
                Arrays.asList(id, name),
                config,
                Collections.singletonList(key),
                Collections.singletonList(distribution));

        DimensionDorisPublishVO preview = service.preview("table-id", request(), dorisDatasource());

        assertTrue(preview.getDdl().contains("UNIQUE KEY(`customer_id`)"));
        assertTrue(preview.getDdl().contains("BUCKETS 8"));
        assertTrue(preview.getDdl().contains("`customer_name` VARCHAR(384)"));
    }

    @Test
    void shouldNotExposeAggregateKeyInV1AndShouldReturnBackendTypeOptions() {
        assertEquals(2, DorisDataModelEnum.values().length);
        assertTrue(service.getFieldTypeOptions().stream()
                .anyMatch(option -> "DECIMAL".equals(option.getCode())
                        && "DECIMAL".equals(option.getParameterType())));
        assertTrue(service.getFieldTypeOptions().stream()
                .anyMatch(option -> "VARCHAR".equals(option.getCode())
                        && "LENGTH".equals(option.getParameterType())));
    }

    private void stubPreview(
            java.util.List<DimensionTableFieldPO> fields,
            DimensionDorisConfigPO config,
            java.util.List<DimensionDorisKeyFieldPO> keys,
            java.util.List<DimensionDorisDistributionFieldPO> distributions) {
        when(dimensionTableDao.queryById("table-id")).thenReturn(table());
        when(dimensionTableFieldDao.queryList(any())).thenReturn(fields);
        when(dimensionDorisConfigDao.queryOne(any())).thenReturn(config);
        when(dimensionDorisKeyFieldDao.queryList(any())).thenReturn(keys);
        when(dimensionDorisDistributionFieldDao.queryList(any())).thenReturn(distributions);
    }

    private DimensionTablePO table() {
        DimensionTablePO table = new DimensionTablePO();
        table.setId("table-id");
        table.setTableType(DimensionTableTypeEnum.DETAIL);
        table.setTableName("dwd_order_detail");
        table.setCnName("订单明细");
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
        dto.setTargetDatabase("warehouse");
        return dto;
    }

    private DataSource dorisDatasource() {
        DataSource datasource = new DataSource();
        datasource.setId(100);
        datasource.setName("test-doris");
        datasource.setType(DbType.DORIS);
        return datasource;
    }
}
