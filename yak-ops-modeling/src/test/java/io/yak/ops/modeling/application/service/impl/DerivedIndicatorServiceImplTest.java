package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.entity.TimePeriodPO;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.modeling.api.dto.derivedindicator.DerivedIndicatorDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DerivedIndicatorServiceImplTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

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
    @Mock
    private DataMartDao dataMartDao;
    @Mock
    private SubjectDomainDao subjectDomainDao;

    @InjectMocks
    private DerivedIndicatorServiceImpl service;

    @Test
    void shouldAllowEmptyModifierAndUnselectedWarehouseLayer() {
        stubCoreReferences();
        DerivedIndicatorDTO dto = baseDto();
        dto.setDwType(null);
        dto.setBusTypeId("stale-business-type");
        dto.setDomainId("stale-domain");
        dto.setBusProcessId("stale-process");
        dto.setMartId("stale-mart");
        dto.setSubjectId("stale-subject");

        assertDoesNotThrow(() -> service.validate(dto));

        assertEquals(null, dto.getModifierId());
        assertEquals(null, dto.getBusTypeId());
        assertEquals(null, dto.getDomainId());
        assertEquals(null, dto.getBusProcessId());
        assertEquals(null, dto.getMartId());
        assertEquals(null, dto.getSubjectId());
    }

    @Test
    void shouldNormalizeAndDeduplicateOptionalModifierIds() {
        stubCoreReferences();
        when(modelingAssetDao.queryById("modifier", "modifier-id")).thenReturn(new io.yak.ops.dao.entity.ModifierPO());
        DerivedIndicatorDTO dto = baseDto();
        dto.setModifierId("modifier-id, modifier-id，");

        service.validate(dto);

        assertEquals("modifier-id", dto.getModifierId());
    }

    @Test
    void shouldRejectUnknownModifierReference() {
        stubCoreReferences();
        DerivedIndicatorDTO dto = baseDto();
        dto.setModifierId("missing-modifier");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));
    }

    @Test
    void shouldRequireDomainAndBusinessProcessForPublicLayer() {
        stubCoreReferences();
        DerivedIndicatorDTO dto = baseDto();
        dto.setDwType(DwTypeEnum.PUBLIC_LAYER);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("公共层必须选择数据域及业务过程", exception.getMessage());
    }

    @Test
    void shouldRejectBusinessProcessOutsideSelectedDomain() {
        stubCoreReferences();
        DataDomainPO domain = new DataDomainPO();
        when(dataDomainDao.queryById("domain-id")).thenReturn(domain);
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId("another-domain");
        when(businessProcessDao.queryById("process-id")).thenReturn(process);

        DerivedIndicatorDTO dto = baseDto();
        dto.setDwType(DwTypeEnum.PUBLIC_LAYER);
        dto.setDomainId("domain-id");
        dto.setBusProcessId("process-id");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("所选业务过程不属于当前数据域", exception.getMessage());
    }

    @Test
    void shouldRequireDataMartAndSubjectDomainForApplicationLayer() {
        stubCoreReferences();
        DerivedIndicatorDTO dto = baseDto();
        dto.setDwType(DwTypeEnum.APPLICATION_LAYER);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("应用层必须选择数据集市", exception.getMessage());
    }

    @Test
    void shouldRejectSubjectDomainFromAnotherDataMart() {
        stubCoreReferences();
        when(dataMartDao.queryById("mart-id")).thenReturn(new DataMartPO());
        SubjectDomainPO subject = new SubjectDomainPO();
        subject.setDataMartId("another-mart");
        when(subjectDomainDao.queryById("subject-id")).thenReturn(subject);

        DerivedIndicatorDTO dto = baseDto();
        dto.setDwType(DwTypeEnum.APPLICATION_LAYER);
        dto.setMartId("mart-id");
        dto.setSubjectId("subject-id");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("所选主题域不属于当前数据集市", exception.getMessage());
    }

    @Test
    void shouldRejectSourceAndUnlayeredEnumValuesAsExplicitWarehouseLayers() {
        stubCoreReferences();
        DerivedIndicatorDTO dto = baseDto();
        dto.setDwType(DwTypeEnum.SOURCE_LAYER);

        assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
    }

    private DerivedIndicatorDTO baseDto() {
        DerivedIndicatorDTO dto = new DerivedIndicatorDTO();
        dto.setCnName("派生指标");
        dto.setEnShortName("derived_indicator");
        dto.setAtomicId("atomic-id");
        dto.setTimePeriodId("time-period-id");
        return dto;
    }

    private void stubCoreReferences() {
        when(modelingAssetDao.queryById("atom-indicator", "atomic-id")).thenReturn(new AtomIndicatorPO());
        when(modelingAssetDao.queryById("time-period", "time-period-id")).thenReturn(new TimePeriodPO());
    }
}
