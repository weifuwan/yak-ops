package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.ComposeIndicatorDao;
import io.yak.ops.dao.repository.modeling.DerivativeIndicatorDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DwLayerServiceImplTest {

    private static final String LAYER_ID = "warehouse-layer-id";

    @Mock
    private DwLayerDao dwLayerDao;

    @Mock
    private DimensionTableDao dimensionTableDao;

    @Mock
    private DerivativeIndicatorDao derivativeIndicatorDao;

    @Mock
    private ComposeIndicatorDao composeIndicatorDao;

    @Mock
    private ModifierDao modifierDao;

    @InjectMocks
    private DwLayerServiceImpl service;

    @Test
    void shouldNormalizeAndCreateValidWarehouseLayer() {
        when(dwLayerDao.queryCount(any())).thenReturn(0L);
        when(dwLayerDao.insert(any())).thenReturn(1);

        String id = service.create(layerDto(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DETAIL), "alice");

        ArgumentCaptor<DwLayerPO> captor = ArgumentCaptor.forClass(DwLayerPO.class);
        verify(dwLayerDao).insert(captor.capture());
        assertNotNull(id);
        assertEquals("DWD_ORDER", captor.getValue().getEnShortName());
        assertEquals(DwModelEnum.DETAIL, captor.getValue().getDwModelType());
        assertEquals("alice", captor.getValue().getCreateBy());
        assertEquals("alice", captor.getValue().getUpdateBy());
    }

    @Test
    void shouldRejectModelTypeNotSupportedByLayer() {
        DwLayerDTO dto = layerDto(DwTypeEnum.SOURCE_LAYER, DwModelEnum.DETAIL);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto));

        assertEquals("当前数仓分层不支持所选模型类型", exception.getMessage());
        verify(dwLayerDao, never()).insert(any());
    }

    @Test
    void shouldAllowUnlayeredRecordWithoutModelType() {
        when(dwLayerDao.queryCount(any())).thenReturn(0L);
        when(dwLayerDao.insert(any())).thenReturn(1);

        String id = service.create(layerDto(DwTypeEnum.NONE_LAYER, null));

        assertNotNull(id);
        verify(dwLayerDao).insert(any());
    }

    @Test
    void shouldRejectDuplicateShortName() {
        when(dwLayerDao.queryCount(any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(layerDto(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DETAIL)));

        assertTrue(exception.getMessage().contains("英文缩写已存在"));
        verify(dwLayerDao, never()).insert(any());
    }

    @Test
    void shouldRejectUpdateWhenLayerDoesNotExist() {
        when(dwLayerDao.queryById(LAYER_ID)).thenReturn(null);
        DwLayerDTO dto = layerDto(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DETAIL);
        dto.setId(LAYER_ID);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateById(dto));

        assertEquals("数仓分层不存在或不属于当前项目", exception.getMessage());
        verify(dwLayerDao, never()).updateById(any());
    }

    @Test
    void shouldKeepLayerAndModelTypesImmutableAfterCreation() {
        DwLayerPO existing = new DwLayerPO();
        existing.setDwType(DwTypeEnum.PUBLIC_LAYER);
        existing.setDwModelType(DwModelEnum.DETAIL);
        when(dwLayerDao.queryById(LAYER_ID)).thenReturn(existing);

        DwLayerDTO dto = layerDto(DwTypeEnum.APPLICATION_LAYER, DwModelEnum.APPLICATION);
        dto.setId(LAYER_ID);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateById(dto));

        assertEquals("数仓分层类型创建后不可修改", exception.getMessage());
        verify(dwLayerDao, never()).updateById(any());
    }

    @Test
    void shouldRejectDeleteWhenAnyModelingAssetReferencesLayer() {
        when(dwLayerDao.queryById(LAYER_ID)).thenReturn(new DwLayerPO());
        when(dimensionTableDao.queryCount(any())).thenReturn(1L);
        when(derivativeIndicatorDao.queryCount(any())).thenReturn(2L);
        when(composeIndicatorDao.queryCount(any())).thenReturn(3L);
        when(modifierDao.queryCount(any())).thenReturn(4L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(LAYER_ID));

        assertTrue(exception.getMessage().contains("维度模型 1 条"));
        assertTrue(exception.getMessage().contains("派生指标 2 条"));
        assertTrue(exception.getMessage().contains("复合指标 3 条"));
        assertTrue(exception.getMessage().contains("指标修饰词 4 条"));
        verify(dwLayerDao, never()).deleteById(LAYER_ID);
    }

    @Test
    void shouldDeleteLayerWhenNoReferencesExist() {
        when(dwLayerDao.queryById(LAYER_ID)).thenReturn(new DwLayerPO());
        when(dimensionTableDao.queryCount(any())).thenReturn(0L);
        when(derivativeIndicatorDao.queryCount(any())).thenReturn(0L);
        when(composeIndicatorDao.queryCount(any())).thenReturn(0L);
        when(modifierDao.queryCount(any())).thenReturn(0L);
        when(dwLayerDao.deleteById(LAYER_ID)).thenReturn(true);

        assertEquals(LAYER_ID, service.delete(LAYER_ID));
        verify(dwLayerDao).deleteById(LAYER_ID);
    }

    @Test
    void shouldUseExactModelTypeCodesAndLayerMappings() {
        assertEquals("DIM", DwModelEnum.DIM.getCode());
        assertTrue(DwModelEnum.canCreate(DwTypeEnum.PUBLIC_LAYER, DwModelEnum.DIMENSION_DIM));
        assertTrue(DwModelEnum.canCreate(DwModelEnum.DIMENSION_DIM, "DIMENSION"));
        assertTrue(DwModelEnum.canCreate(DwModelEnum.DIMENSION_DIM, "DIM"));
        assertFalse(DwModelEnum.canCreate(DwModelEnum.DIMENSION_DIM, "IM"));
        assertFalse(DwModelEnum.canCreate(DwTypeEnum.SOURCE_LAYER, DwModelEnum.DETAIL));
    }

    private DwLayerDTO layerDto(DwTypeEnum layerType, DwModelEnum modelType) {
        DwLayerDTO dto = new DwLayerDTO();
        dto.setEnShortName("dwd_order");
        dto.setEnName("Dwd Order");
        dto.setCnName("订单明细层");
        dto.setDirector("owner");
        dto.setDwType(layerType);
        dto.setDwModelType(modelType);
        return dto;
    }
}
