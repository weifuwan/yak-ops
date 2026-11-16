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
import io.yak.ops.modeling.application.service.DwLayerService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelingAssetServiceImplTest {

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
    private ModelingAssetServiceImpl service;

    @Test
    void shouldRejectOdsLayerWhenCreatingModifier() {
        DwLayerPO ods = new DwLayerPO();
        ods.setDwType(DwTypeEnum.SOURCE_LAYER);
        when(dwLayerDao.queryById("ods-layer")).thenReturn(ods);

        Map<String, Object> payload = modifierPayload("SIMPLE");
        payload.put("dwhId", "ods-layer");

        assertThrows(IllegalArgumentException.class, () -> service.create("modifier", payload));
        verify(modelingAssetDao, never()).insert(eq("modifier"), any(ModifierPO.class));
    }

    @Test
    void shouldAllowPublicLayerWhenCreatingModifier() {
        DwLayerPO publicLayer = new DwLayerPO();
        publicLayer.setDwType(DwTypeEnum.PUBLIC_LAYER);
        when(dwLayerDao.queryById("public-layer")).thenReturn(publicLayer);
        when(modelingAssetDao.insert(eq("modifier"), any(ModifierPO.class))).thenReturn(1);

        Map<String, Object> payload = modifierPayload("SIMPLE");
        payload.put("dwhId", "public-layer");

        String id = service.create("modifier", payload);

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

        Map<String, Object> payload = modifierPayload("DIMENSION");
        payload.put("dimensionTbId", "dimension-table");
        payload.put("dimensionFdId", "dimension-field");
        payload.put("codeId", "selected-code");
        payload.put("codeDetailId", "selected-detail");

        assertThrows(IllegalArgumentException.class, () -> service.create("modifier", payload));
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

        assertThrows(IllegalArgumentException.class, () -> service.submit("modifier", "modifier-id"));
        verify(modelingAssetDao, never()).updateById(eq("modifier"), any(ModifierPO.class));
    }

    @Test
    void shouldRejectDerivativeIndicatorWithUnknownModifierReference() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("enShortName", "daily_active_user");
        payload.put("cnName", "日活跃用户数");
        payload.put("atomicId", "atomic-id");
        payload.put("timePeriodId", "time-period-id");
        payload.put("modifierId", "missing-modifier");

        assertThrows(IllegalArgumentException.class, () -> service.create("derivative-indicator", payload));
        verify(modelingAssetDao, never()).insert(eq("derivative-indicator"), any());
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

        assertThrows(IllegalArgumentException.class, () -> service.submit("modifier", "modifier-id"));
        verify(modelingAssetDao, never()).updateById(eq("modifier"), any(ModifierPO.class));
    }

    private Map<String, Object> modifierPayload(String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("modifierType", type);
        payload.put("enShortName", "active_user");
        payload.put("cnName", "活跃用户");
        payload.put("businessCaliber", "符合活跃定义的用户");
        payload.put("director", "owner");
        return payload;
    }
}
