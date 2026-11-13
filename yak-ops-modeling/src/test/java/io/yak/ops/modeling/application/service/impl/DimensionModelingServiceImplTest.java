package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableVO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.application.service.DwLayerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        when(dimensionTableDao.insert(any())).thenReturn(1);
        when(versionManagementDao.queryOne(any())).thenReturn(null);

        DimensionTableVO result = service.create(dimensionDto(DimensionTableTypeEnum.DIM));

        assertEquals("公共维度层", result.getDwhName());
        verify(dimensionTableDao).insert(any());
    }

    @Test
    void shouldAllowUnlayeredDimensionModelWithoutModelType() {
        DwLayerVO layer = layer(DwTypeEnum.NONE_LAYER, null);
        layer.setCnName("未分层");
        when(dwLayerService.selectById(LAYER_ID)).thenReturn(layer);
        when(dimensionTableDao.insert(any())).thenReturn(1);
        when(versionManagementDao.queryOne(any())).thenReturn(null);

        DimensionTableVO result = service.create(dimensionDto(DimensionTableTypeEnum.DETAIL));

        assertEquals("未分层", result.getDwhName());
        verify(dimensionTableDao).insert(any());
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
        return dto;
    }
}
