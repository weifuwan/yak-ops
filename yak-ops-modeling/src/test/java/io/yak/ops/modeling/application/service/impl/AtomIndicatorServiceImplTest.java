package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.modeling.api.dto.atomindicator.AtomIndicatorDTO;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AtomIndicatorServiceImplTest {

    private static final String BUSINESS_TYPE_ID = "business-type-id";
    private static final String DOMAIN_ID = "domain-id";
    private static final String PROCESS_ID = "process-id";

    @Mock
    private ModelingAssetDao modelingAssetDao;

    @Mock
    private BusinessTypeDao businessTypeDao;

    @Mock
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Mock
    private DataDomainDao dataDomainDao;

    @Mock
    private BusinessProcessDao businessProcessDao;

    @InjectMocks
    private AtomIndicatorServiceImpl service;

    @Test
    void shouldRequireBusinessProcessAndDomain() {
        AtomIndicatorDTO dto = validDto();
        dto.setBusProcessId(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto, "alice"));

        assertEquals("busProcessId不能为空", exception.getMessage());
        verify(modelingAssetDao, never()).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    @Test
    void shouldRejectUnknownBusinessType() {
        AtomIndicatorDTO dto = validDto();
        dto.setBusTypeId(BUSINESS_TYPE_ID);
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto, "alice"));

        assertEquals("所选业务分类不存在或不属于当前项目", exception.getMessage());
        verify(dataDomainDao, never()).queryById(DOMAIN_ID);
        verify(modelingAssetDao, never()).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    @Test
    void shouldRejectBusinessProcessFromAnotherDomain() {
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId("another-domain");
        when(businessProcessDao.queryById(PROCESS_ID)).thenReturn(process);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(validDto(), "alice"));

        assertEquals("业务过程不属于所选数据域", exception.getMessage());
        verify(modelingAssetDao, never()).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    @Test
    void shouldRejectDomainNotRelatedToSelectedBusinessType() {
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(new BusinessTypePO());
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId(DOMAIN_ID);
        when(businessProcessDao.queryById(PROCESS_ID)).thenReturn(process);
        BusinessTypeRelatedDataDomainPO relation = new BusinessTypeRelatedDataDomainPO();
        relation.setDataId("another-domain");
        when(businessTypeRelatedDataDomainDao.queryList(any())).thenReturn(Collections.singletonList(relation));

        AtomIndicatorDTO dto = validDto();
        dto.setBusTypeId(BUSINESS_TYPE_ID);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto, "alice"));

        assertEquals("所选数据域未关联到当前业务分类", exception.getMessage());
        verify(modelingAssetDao, never()).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    @Test
    void shouldCreateWhenBusinessTypeHasNoRelatedDomains() {
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(new BusinessTypePO());
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId(DOMAIN_ID);
        when(businessProcessDao.queryById(PROCESS_ID)).thenReturn(process);
        when(businessTypeRelatedDataDomainDao.queryList(any())).thenReturn(Collections.emptyList());
        when(modelingAssetDao.queryCount(eq("atom-indicator"), any())).thenReturn(0L);
        when(modelingAssetDao.insert(eq("atom-indicator"), any(AtomIndicatorPO.class))).thenReturn(1);

        AtomIndicatorDTO dto = validDto();
        dto.setBusTypeId(BUSINESS_TYPE_ID);

        String id = service.create(dto, "alice");

        assertEquals(32, id.length());
        verify(modelingAssetDao).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    @Test
    void shouldCreateWithValidatedReferencesAndDefaultCalculationValues() {
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(new BusinessTypePO());
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId(DOMAIN_ID);
        when(businessProcessDao.queryById(PROCESS_ID)).thenReturn(process);
        BusinessTypeRelatedDataDomainPO relation = new BusinessTypeRelatedDataDomainPO();
        relation.setDataId(DOMAIN_ID);
        when(businessTypeRelatedDataDomainDao.queryList(any())).thenReturn(Collections.singletonList(relation));
        when(modelingAssetDao.queryCount(eq("atom-indicator"), any())).thenReturn(0L);
        when(modelingAssetDao.insert(eq("atom-indicator"), any(AtomIndicatorPO.class))).thenReturn(1);

        AtomIndicatorDTO dto = validDto();
        dto.setBusTypeId(BUSINESS_TYPE_ID);

        String id = service.create(dto, "alice");

        ArgumentCaptor<AtomIndicatorPO> captor = ArgumentCaptor.forClass(AtomIndicatorPO.class);
        verify(modelingAssetDao).insert(eq("atom-indicator"), captor.capture());
        assertEquals(32, id.length());
        assertEquals(DwTypeEnum.PUBLIC_LAYER, captor.getValue().getDwType());
        assertEquals(Integer.valueOf(0), captor.getValue().getDecimalNumber());
        assertEquals(Boolean.TRUE, captor.getValue().getRemoveDuplicates());
        assertEquals(Boolean.FALSE, captor.getValue().getSubmit());
    }

    @Test
    void shouldForcePublicClassificationEvenWhenAnotherTypeIsProvided() {
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId(DOMAIN_ID);
        when(businessProcessDao.queryById(PROCESS_ID)).thenReturn(process);
        when(modelingAssetDao.queryCount(eq("atom-indicator"), any())).thenReturn(0L);
        when(modelingAssetDao.insert(eq("atom-indicator"), any(AtomIndicatorPO.class))).thenReturn(1);

        AtomIndicatorDTO dto = validDto();
        dto.setDwType(DwTypeEnum.APPLICATION_LAYER);

        service.create(dto, "alice");

        ArgumentCaptor<AtomIndicatorPO> captor = ArgumentCaptor.forClass(AtomIndicatorPO.class);
        verify(modelingAssetDao).insert(eq("atom-indicator"), captor.capture());
        assertEquals(DwTypeEnum.PUBLIC_LAYER, captor.getValue().getDwType());
    }

    @Test
    void shouldRejectNegativeDecimalNumber() {
        AtomIndicatorDTO dto = validDto();
        dto.setDecimalNumber(Integer.valueOf(-1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto, "alice"));

        assertEquals("小数位数不能小于0", exception.getMessage());
        verify(modelingAssetDao, never()).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    @Test
    void shouldRejectUnsupportedCalculationFunction() {
        AtomIndicatorDTO dto = validDto();
        dto.setCalculateFunction("MEDIAN");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto, "alice"));

        assertEquals("计算函数不合法", exception.getMessage());
        verify(dataDomainDao, never()).queryById(DOMAIN_ID);
        verify(modelingAssetDao, never()).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    @Test
    void shouldRejectUnitThatDoesNotBelongToCurrentProject() {
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId(DOMAIN_ID);
        when(businessProcessDao.queryById(PROCESS_ID)).thenReturn(process);
        when(modelingAssetDao.queryById("unit-standard", "unit-id")).thenReturn(null);

        AtomIndicatorDTO dto = validDto();
        dto.setUnitId("unit-id");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto, "alice"));

        assertEquals("度量单位不存在或不属于当前项目", exception.getMessage());
        verify(modelingAssetDao, never()).insert(eq("atom-indicator"), any(AtomIndicatorPO.class));
    }

    private AtomIndicatorDTO validDto() {
        AtomIndicatorDTO dto = new AtomIndicatorDTO();
        dto.setEnShortName("active_user_count");
        dto.setEnName("Active user count");
        dto.setCnName("活跃用户数");
        dto.setBusinessCaliber("符合活跃定义的用户数量");
        dto.setDirector("owner");
        dto.setDomainId(DOMAIN_ID);
        dto.setBusProcessId(PROCESS_ID);
        return dto;
    }
}
