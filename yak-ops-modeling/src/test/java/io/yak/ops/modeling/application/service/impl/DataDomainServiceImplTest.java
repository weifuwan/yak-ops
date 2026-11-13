package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.dto.datadomain.DataDomainDTO;
import io.yak.ops.modeling.application.service.BusinessProcessService;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class DataDomainServiceImplTest {

    private static final String DOMAIN_ID = "domain-id";

    @Mock
    private BusinessProcessService businessProcessService;

    @Mock
    private BusinessProcessDao businessProcessDao;

    @Mock
    private DataDomainDao dataDomainDao;

    @Mock
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Mock
    private DimensionTableDao dimensionTableDao;

    @InjectMocks
    private DataDomainServiceImpl service;

    @Test
    void shouldCreateDefaultBusinessProcessWithCurrentOperator() {
        when(dataDomainDao.queryCount(any())).thenReturn(0L);
        when(dataDomainDao.insert(any())).thenReturn(1);
        when(businessProcessService.create(any(BusinessProcessDTO.class), anyString()))
                .thenReturn("default-process-id");

        DataDomainDTO dto = new DataDomainDTO();
        dto.setEnShortName("patient");
        dto.setEnName("Patient");
        dto.setCnName("患者");
        dto.setDirector("owner");

        String id = service.create(dto, "alice");

        assertNotNull(id);
        ArgumentCaptor<BusinessProcessDTO> captor = ArgumentCaptor.forClass(BusinessProcessDTO.class);
        verify(businessProcessService).create(captor.capture(), eq("alice"));
        assertEquals(id, captor.getValue().getDataDomainId());
        assertEquals(1, captor.getValue().getFlag());
        assertEquals("owner", captor.getValue().getDirector());
    }

    @Test
    void shouldProtectSystemDataDomainFromDeletion() {
        DataDomainPO domain = new DataDomainPO();
        domain.setFlag(1);
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(domain);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(DOMAIN_ID));

        assertEquals("系统默认数据域不能删除", exception.getMessage());
        verify(dataDomainDao, never()).deleteById(DOMAIN_ID);
    }

    @Test
    void shouldRejectDeleteWhenDimensionTablesReferenceDomainOrItsProcesses() {
        DataDomainPO domain = new DataDomainPO();
        domain.setFlag(0);
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(domain);
        when(businessProcessDao.queryList(any())).thenReturn(Collections.<BusinessProcessPO>emptyList());
        when(dimensionTableDao.queryCount(any())).thenReturn(2L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(DOMAIN_ID));

        assertEquals("该数据域或其业务过程已被维度模型引用（2条），不能删除", exception.getMessage());
        verify(businessProcessService, never()).deleteByDomainId(DOMAIN_ID);
        verify(dataDomainDao, never()).deleteById(DOMAIN_ID);
    }

    @Test
    void shouldRemoveBusinessProcessAndBusinessTypeLinksWhenDomainHasNoReferences() {
        DataDomainPO domain = new DataDomainPO();
        domain.setFlag(0);
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(domain);
        when(businessProcessDao.queryList(any())).thenReturn(Collections.<BusinessProcessPO>emptyList());
        when(dimensionTableDao.queryCount(any())).thenReturn(0L);
        when(dataDomainDao.deleteById(DOMAIN_ID)).thenReturn(true);

        assertEquals(DOMAIN_ID, service.delete(DOMAIN_ID));

        verify(businessProcessService).deleteByDomainId(DOMAIN_ID);
        verify(businessTypeRelatedDataDomainDao).deleteByWrapper(any());
        verify(dataDomainDao).deleteById(DOMAIN_ID);
    }
}
