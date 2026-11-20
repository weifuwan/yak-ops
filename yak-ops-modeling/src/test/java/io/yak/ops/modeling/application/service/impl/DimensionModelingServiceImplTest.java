package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.FieldStandardDao;
import io.yak.ops.dao.repository.modeling.CodeStandardDao;
import io.yak.ops.dao.repository.modeling.UnitStandardDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableFieldDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableVO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.application.service.DimensionTreeBuilder;
import io.yak.ops.modeling.application.service.DwLayerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DimensionModelingServiceImplTest {

    private static final String LAYER_ID = "warehouse-layer-id";

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DimensionTableDao dimensionTableDao;

    @Mock
    private BusinessProcessDao businessProcessDao;

    @Mock
    private BusinessTypeDao businessTypeDao;

    @Mock
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Mock
    private DataDomainDao dataDomainDao;

    @Mock
    private DataMartDao dataMartDao;
    
    @Mock
    private FieldStandardDao fieldStandardDao;

    @Mock
    private CodeStandardDao codeStandardDao;

    @Mock
    private UnitStandardDao unitStandardDao;

    @Mock
    private SubjectDomainDao subjectDomainDao;

    @Mock
    private ModifierDao modifierDao;

    @Mock
    private DimensionTableFieldDao dimensionTableFieldDao;

    @Mock
    private VersionManagementDao versionManagementDao;

    @Mock
    private DwLayerService dwLayerService;

    @Mock
    private DimensionTreeBuilder dimensionTreeBuilder;

    @InjectMocks
    private DimensionModelingServiceImpl service;

    @Test
    void shouldRejectDimensionTableTypeNotSupportedByWarehouseLayer() {
        DwLayerVO layer = layer(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.SUMMARY);
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer);

        DimensionTableDTO dto = dimensionDto(DimensionTableTypeEnum.DETAIL);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto));

        assertEquals("当前数仓分层不支持所选维度模型类型", exception.getMessage());
        verify(dimensionTableDao, never()).insert(any());
    }

    @Test
    void shouldAllowDimensionAndDimensionTableInCombinedLayer() {
        DwLayerVO layer = layer(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DIMENSION_DIM);
        layer.setCnName("公共维度层");
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer);
        when(dataDomainDao.queryById("domain-a")).thenReturn(new DataDomainPO());
        when(dimensionTableDao.insert(any())).thenReturn(1);
        when(versionManagementDao.queryOne(any())).thenReturn(null);

        DimensionTableDTO dto = dimensionDto(DimensionTableTypeEnum.DIM);
        dto.setDomainId("domain-a");
        DimensionTableVO result = service.create(dto);

        assertEquals("公共维度层", result.getDwhName());
        ArgumentCaptor<DimensionTablePO> created = ArgumentCaptor.forClass(DimensionTablePO.class);
        verify(dimensionTableDao).insert(created.capture());
        assertEquals("sample_dimension", created.getValue().getEnShortName());
        assertNull(created.getValue().getTableName());
    }

    @Test
    void shouldRejectCreateInNoneLayer() {
        DwLayerVO layer = layer(DwTypeEnum.NONE_LAYER, null);
        layer.setCnName("未分层");
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dimensionDto(DimensionTableTypeEnum.SOURCE)));

        assertEquals("未分层仅用于历史数据管理，不能在未分层下新建模型或迁入未分层", exception.getMessage());
        verify(dimensionTableDao, never()).insert(any());
    }

    @Test
    void shouldAllowHistoricalUnlayeredRecordToRemainUnlayered() {
        DwLayerVO layer = layer(DwTypeEnum.NONE_LAYER, null);
        layer.setCnName("未分层");
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer);
        DimensionTablePO existing = new DimensionTablePO();
        existing.setId("table-id");
        existing.setDwhId(LAYER_ID);
        existing.setTableType(DimensionTableTypeEnum.SOURCE);
        existing.setTableName("ods_customer");
        existing.setCnName("客户源表");
        existing.setDirector("owner");
        when(dimensionTableDao.queryById("table-id")).thenReturn(existing);
        when(dimensionTableDao.updateById(any())).thenReturn(true);
        when(dimensionTableDao.update(any(), any())).thenReturn(1);
        when(versionManagementDao.queryOne(any())).thenReturn(null);

        DimensionTableDTO dto = dimensionDto(DimensionTableTypeEnum.SOURCE);
        dto.setId("table-id");
        DimensionTableVO result = service.updateById(dto);

        assertEquals("未分层", result.getDwhName());
        verify(dimensionTableDao).updateById(any());
    }

    @Test
    void shouldRejectFieldStandardReferencesOutsideCurrentProject() {
        DimensionTablePO table = new DimensionTablePO();
        table.setId("table-id");
        when(dimensionTableDao.queryById("table-id")).thenReturn(table);
        when(fieldStandardDao.queryById("missing-standard")).thenReturn(null);

        DimensionTableFieldDTO field = new DimensionTableFieldDTO();
        field.setFieldName("customer_id");
        field.setDataType(DimensionDataTypeEnum.BIGINT);
        field.setFieldStdId("missing-standard");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createField("table-id", field));

        assertEquals("关联字段标准不存在或不属于当前项目", exception.getMessage());
        verify(dimensionTableFieldDao, never()).insert(any());
    }

    @Test
    void shouldRequireFieldsBeforeSubmittingLogicalTables() {
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DETAIL));
        when(dataDomainDao.queryById("domain-a")).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId("domain-a");
        when(businessProcessDao.queryById("process-id")).thenReturn(process);
        DimensionTablePO existing = new DimensionTablePO();
        existing.setId("table-id");
        existing.setDwhId(LAYER_ID);
        existing.setTableType(DimensionTableTypeEnum.DETAIL);
        existing.setTableName("dwd_order_detail");
        existing.setCnName("订单明细");
        existing.setDirector("owner");
        existing.setDomainId("domain-a");
        existing.setBusProcessId("process-id");
        when(dimensionTableDao.queryById("table-id")).thenReturn(existing);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit("table-id"));

        assertEquals("提交前至少需要配置一个字段", exception.getMessage());
        verify(dimensionTableDao, never()).updateById(any());
    }

    @Test
    void shouldAllowBusinessDimensionSubmissionWithoutPhysicalFields() {
        DwLayerVO layer = layer(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DIMENSION_DIM);
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer);
        when(dataDomainDao.queryById("domain-a")).thenReturn(new DataDomainPO());
        DimensionTablePO existing = new DimensionTablePO();
        existing.setId("dimension-id");
        existing.setDwhId(LAYER_ID);
        existing.setTableType(DimensionTableTypeEnum.DIM);
        existing.setEnShortName("customer");
        existing.setCnName("客户维度");
        existing.setDomainId("domain-a");
        existing.setSubmit(Boolean.FALSE);
        when(dimensionTableDao.queryById("dimension-id")).thenReturn(existing);
        when(dimensionTableDao.updateById(any())).thenReturn(true);
        when(versionManagementDao.queryOne(any())).thenReturn(null);

        DimensionTableVO result = service.submit("dimension-id");

        assertEquals("客户维度", result.getCnName());
        verify(dimensionTableDao).updateById(any());
    }

    @Test
    void shouldRejectBusinessProcessFromAnotherDomain() {
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DETAIL));
        when(dataDomainDao.queryById("domain-a")).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId("domain-b");
        when(businessProcessDao.queryById("process-id")).thenReturn(process);

        DimensionTableDTO dto = dimensionDto(DimensionTableTypeEnum.DETAIL);
        dto.setDomainId("domain-a");
        dto.setBusProcessId("process-id");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto));

        assertEquals("业务过程不属于所选数据域", exception.getMessage());
        verify(dimensionTableDao, never()).insert(any());
    }

    @Test
    void shouldRejectDomainNotAssociatedWithSelectedBusinessType() {
        when(dataDomainDao.queryById("domain-a")).thenReturn(new DataDomainPO());
        when(businessTypeDao.queryById("business-type-id")).thenReturn(new BusinessTypePO());
        when(businessTypeRelatedDataDomainDao.queryCount(any())).thenReturn(0L);

        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DIMENSION));
        DimensionTableDTO dto = dimensionDto(DimensionTableTypeEnum.DIMENSION);
        dto.setDomainId("domain-a");
        dto.setBusTypeId("business-type-id");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto));

        assertEquals("所选数据域未关联到当前业务分类", exception.getMessage());
        verify(dimensionTableDao, never()).insert(any());
    }

    private DwLayerVO layer(DwTypeEnum layerType, DwModelEnum modelType) {
        DwLayerVO layer = new DwLayerVO();
        layer.setId(LAYER_ID);
        layer.setDwType(layerType);
        layer.setDwModelType(modelType);
        return layer;
    }

    private DimensionTableDTO dimensionDto(DimensionTableTypeEnum tableType) {
        DimensionTableDTO dto = new DimensionTableDTO();
        dto.setDwhId(LAYER_ID);
        dto.setTableType(tableType);
        dto.setTableName(tableType == DimensionTableTypeEnum.DIM ? null : "sample_" + tableType.name().toLowerCase());
        dto.setEnShortName(tableType == DimensionTableTypeEnum.DIM ? "sample_dimension" : null);
        dto.setCnName("示例" + tableType.getName());
        dto.setDirector("owner");
        return dto;
    }
}
