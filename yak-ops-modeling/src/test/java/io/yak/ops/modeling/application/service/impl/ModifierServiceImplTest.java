package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.CodeDetailPO;
import io.yak.ops.dao.entity.CodeStandardPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.entity.ModifierPO;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.modeling.api.dto.modifier.ModifierDTO;
import io.yak.ops.modeling.application.service.DwLayerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModifierServiceImplTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ModelingAssetDao modelingAssetDao;

    @Mock
    private DwLayerDao dwLayerDao;

    @Mock
    private DwLayerService dwLayerService;

    @Mock
    private BusinessTypeDao businessTypeDao;

    @Mock
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Mock
    private DataDomainDao dataDomainDao;

    @Mock
    private DimensionTableDao dimensionTableDao;

    @Mock
    private DimensionTableFieldDao dimensionTableFieldDao;

    @InjectMocks
    private ModifierServiceImpl service;

    @Test
    void shouldRejectOdsLayerWhenCreatingModifier() {
        DwLayerPO ods = new DwLayerPO();
        ods.setDwType(DwTypeEnum.SOURCE_LAYER);
        when(dwLayerDao.queryById("ods-layer")).thenReturn(ods);

        ModifierDTO dto = simpleModifier();
        dto.setDwhId("ods-layer");

        assertThrows(IllegalArgumentException.class, () -> service.create(dto, "alice"));
        verify(modelingAssetDao, never()).insert(eq("modifier"), any(ModifierPO.class));
    }

    @Test
    void shouldAllowPublicLayerWhenCreatingModifier() {
        DwLayerPO publicLayer = new DwLayerPO();
        publicLayer.setDwType(DwTypeEnum.PUBLIC_LAYER);
        when(dwLayerDao.queryById("public-layer")).thenReturn(publicLayer);
        when(modelingAssetDao.insert(eq("modifier"), any(ModifierPO.class))).thenReturn(1);

        ModifierDTO dto = simpleModifier();
        dto.setDwhId("public-layer");

        String id = service.create(dto, "alice");

        assertNotNull(id);
        verify(modelingAssetDao).insert(eq("modifier"), any(ModifierPO.class));
    }

    @Test
    void shouldRejectDimensionCodeDetailThatBelongsToAnotherCode() {
        DimensionTablePO dimensionTable = new DimensionTablePO();
        dimensionTable.setTableType(DimensionTableTypeEnum.DIM);
        DimensionTableFieldPO dimensionField = new DimensionTableFieldPO();
        dimensionField.setTableId("dimension-table");
        dimensionField.setCodeStdId("selected-code");
        CodeDetailPO codeDetail = new CodeDetailPO();
        codeDetail.setCodeId("different-code");

        when(dimensionTableDao.queryById("dimension-table")).thenReturn(dimensionTable);
        when(dimensionTableFieldDao.queryOne(any())).thenReturn(dimensionField);
        when(modelingAssetDao.queryById("code-standard", "selected-code")).thenReturn(new CodeStandardPO());
        when(modelingAssetDao.queryById("code-detail", "selected-detail")).thenReturn(codeDetail);

        ModifierDTO dto = simpleModifier();
        dto.setModifierType("DIMENSION");
        dto.setDimensionTbId("dimension-table");
        dto.setDimensionFdId("dimension-field");
        dto.setCodeId("selected-code");
        dto.setCodeDetailId("selected-detail");

        assertThrows(IllegalArgumentException.class, () -> service.create(dto, "alice"));
        verify(modelingAssetDao, never()).insert(eq("modifier"), any(ModifierPO.class));
    }

    @Test
    void shouldRequireCompleteDimensionConfigurationBeforeSubmit() {
        ModifierPO modifier = new ModifierPO();
        modifier.setId("modifier-id");
        modifier.setModifierType("DIMENSION");
        modifier.setEnShortName("dimension_modifier");
        modifier.setCnName("维度修饰词");
        modifier.setBusinessCaliber("维度枚举限定");
        modifier.setDirector("owner");
        when(modelingAssetDao.queryById("modifier", "modifier-id")).thenReturn(modifier);

        assertThrows(IllegalArgumentException.class, () -> service.submit("modifier-id", "alice"));
        verify(modelingAssetDao, never()).updateById(eq("modifier"), any(ModifierPO.class));
    }

    @Test
    void shouldRejectOdsLayerWhenSubmittingExistingModifier() {
        ModifierPO modifier = new ModifierPO();
        modifier.setId("modifier-id");
        modifier.setModifierType("SIMPLE");
        modifier.setEnShortName("simple_modifier");
        modifier.setCnName("普通修饰词");
        modifier.setBusinessCaliber("业务限定");
        modifier.setDirector("owner");
        modifier.setDwhId("ods-layer");
        DwLayerPO ods = new DwLayerPO();
        ods.setDwType(DwTypeEnum.SOURCE_LAYER);

        when(modelingAssetDao.queryById("modifier", "modifier-id")).thenReturn(modifier);
        when(dwLayerDao.queryById("ods-layer")).thenReturn(ods);

        assertThrows(IllegalArgumentException.class, () -> service.submit("modifier-id", "alice"));
        verify(modelingAssetDao, never()).updateById(eq("modifier"), any(ModifierPO.class));
    }

    @Test
    void shouldRemoveVersionHistoryWhenDeletingModifier() {
        ModifierPO modifier = new ModifierPO();
        modifier.setId("modifier-id");
        modifier.setModifierType("SIMPLE");
        when(modelingAssetDao.queryById("modifier", "modifier-id")).thenReturn(modifier);
        when(modelingAssetDao.deleteById("modifier", "modifier-id")).thenReturn(1);

        service.delete("modifier-id");

        verify(modelingAssetDao).deleteByWrapper(eq("version-management"), any());
    }

    private ModifierDTO simpleModifier() {
        ModifierDTO dto = new ModifierDTO();
        dto.setModifierType("SIMPLE");
        dto.setEnShortName("active_user");
        dto.setCnName("活跃用户");
        dto.setBusinessCaliber("符合活跃定义的用户");
        dto.setDirector("owner");
        return dto;
    }
}
