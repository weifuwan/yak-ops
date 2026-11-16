package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.modeling.api.dto.compositeindicator.CompositeIndicatorDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeIndicatorServiceImplTest {

    @Mock
    private ModelingAssetDao modelingAssetDao;
    @Mock
    private DwLayerDao dwLayerDao;
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
    private CompositeIndicatorServiceImpl service;

    @Test
    void shouldValidateExpressionModeAndClearComparisonFields() {
        stubPublicLayer();
        CompositeIndicatorDTO dto = baseDto();
        dto.setComputeMode("0");
        dto.setComputeExpr("@sales + @orders");
        dto.setDeriveId("stale-derived-id");
        dto.setCompareType("DAY_ON_LAST_DAY");

        assertDoesNotThrow(() -> service.validate(dto));

        assertNull(dto.getDeriveId());
        assertNull(dto.getCompareType());
    }

    @Test
    void shouldValidateRatioModeAndClearExpression() {
        stubPublicLayer();
        when(modelingAssetDao.queryById("derivative-indicator", "derived-id"))
                .thenReturn(new DerivativeIndicatorPO());
        CompositeIndicatorDTO dto = baseDto();
        dto.setComputeMode("1");
        dto.setComputeExpr("stale expression");
        dto.setDeriveId("derived-id");
        dto.setCompareType("MONTH_ON_MONTH_LAST_YEAR");

        assertDoesNotThrow(() -> service.validate(dto));

        assertNull(dto.getComputeExpr());
    }

    @Test
    void shouldRejectUnknownComparisonType() {
        CompositeIndicatorDTO dto = baseDto();
        dto.setComputeMode("1");
        dto.setDeriveId("derived-id");
        dto.setCompareType("UNKNOWN");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("对比类型不正确", exception.getMessage());
    }

    @Test
    void shouldRequireExpressionInExpressionMode() {
        CompositeIndicatorDTO dto = baseDto();
        dto.setComputeMode("0");
        dto.setComputeExpr(" ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("computeExpr不能为空", exception.getMessage());
    }

    @Test
    void shouldRequireBusinessProcessForPublicLayer() {
        DwLayerPO layer = new DwLayerPO();
        layer.setDwType(DwTypeEnum.PUBLIC_LAYER);
        when(dwLayerDao.queryById("public-layer")).thenReturn(layer);
        CompositeIndicatorDTO dto = baseDto();
        dto.setDomainId(null);
        dto.setBusProcessId(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("公共层必须选择数据域及业务过程", exception.getMessage());
    }

    @Test
    void shouldRejectBusinessProcessOutsideSelectedDomain() {
        DwLayerPO layer = new DwLayerPO();
        layer.setDwType(DwTypeEnum.PUBLIC_LAYER);
        when(dwLayerDao.queryById("public-layer")).thenReturn(layer);
        when(dataDomainDao.queryById("domain-id")).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId("another-domain");
        when(businessProcessDao.queryById("process-id")).thenReturn(process);

        CompositeIndicatorDTO dto = baseDto();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("所选业务过程不属于当前数据域", exception.getMessage());
    }

    @Test
    void shouldValidateApplicationOwnershipAndClearPublicFields() {
        DwLayerPO layer = new DwLayerPO();
        layer.setDwType(DwTypeEnum.APPLICATION_LAYER);
        when(dwLayerDao.queryById("application-layer")).thenReturn(layer);
        when(dataMartDao.queryById("mart-id")).thenReturn(new DataMartPO());
        SubjectDomainPO subject = new SubjectDomainPO();
        subject.setDataMartId("mart-id");
        when(subjectDomainDao.queryById("subject-id")).thenReturn(subject);

        CompositeIndicatorDTO dto = baseDto();
        dto.setDwhId("application-layer");
        dto.setMartId("mart-id");
        dto.setSubjectId("subject-id");
        dto.setBusTypeId("stale-business-type");
        dto.setDomainId("stale-domain");
        dto.setBusProcessId("stale-process");

        assertDoesNotThrow(() -> service.validate(dto));

        assertNull(dto.getBusTypeId());
        assertNull(dto.getDomainId());
        assertNull(dto.getBusProcessId());
    }

    @Test
    void shouldRejectSubjectDomainFromAnotherDataMart() {
        DwLayerPO layer = new DwLayerPO();
        layer.setDwType(DwTypeEnum.APPLICATION_LAYER);
        when(dwLayerDao.queryById("application-layer")).thenReturn(layer);
        when(dataMartDao.queryById("mart-id")).thenReturn(new DataMartPO());
        SubjectDomainPO subject = new SubjectDomainPO();
        subject.setDataMartId("another-mart");
        when(subjectDomainDao.queryById("subject-id")).thenReturn(subject);

        CompositeIndicatorDTO dto = baseDto();
        dto.setDwhId("application-layer");
        dto.setMartId("mart-id");
        dto.setSubjectId("subject-id");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(dto));

        assertEquals("所选主题域不属于当前数据集市", exception.getMessage());
    }

    @Test
    void shouldRejectSourceAndUnlayeredWarehouseLayers() {
        DwLayerPO layer = new DwLayerPO();
        layer.setDwType(DwTypeEnum.SOURCE_LAYER);
        when(dwLayerDao.queryById("public-layer")).thenReturn(layer);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.validate(baseDto()));

        assertEquals("复合指标仅支持公共层或应用层，不能关联贴源层或未分层", exception.getMessage());
    }

    private CompositeIndicatorDTO baseDto() {
        CompositeIndicatorDTO dto = new CompositeIndicatorDTO();
        dto.setDwhId("public-layer");
        dto.setComputeMode("0");
        dto.setComputeExpr("metric_a + metric_b");
        dto.setEnShortName("composite_metric");
        dto.setCnName("复合指标");
        dto.setDirector("admin");
        dto.setDomainId("domain-id");
        dto.setBusProcessId("process-id");
        return dto;
    }

    private void stubPublicLayer() {
        DwLayerPO layer = new DwLayerPO();
        layer.setDwType(DwTypeEnum.PUBLIC_LAYER);
        when(dwLayerDao.queryById("public-layer")).thenReturn(layer);
        when(dataDomainDao.queryById("domain-id")).thenReturn(new DataDomainPO());
        BusinessProcessPO process = new BusinessProcessPO();
        process.setDataDomainId("domain-id");
        when(businessProcessDao.queryById("process-id")).thenReturn(process);
    }
}
