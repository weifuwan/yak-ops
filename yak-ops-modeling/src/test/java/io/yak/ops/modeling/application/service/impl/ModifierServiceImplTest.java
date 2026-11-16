package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.entity.ModifierPO;
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
