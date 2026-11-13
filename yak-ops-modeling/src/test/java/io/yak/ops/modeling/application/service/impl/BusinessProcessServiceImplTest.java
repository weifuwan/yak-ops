package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class BusinessProcessServiceImplTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String PROCESS_ID = "process-id";

    @Mock
    private BusinessProcessDao businessProcessDao;

    @Mock
    private DataDomainDao dataDomainDao;

    @Mock
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Mock
    private DimensionTableDao dimensionTableDao;

    @InjectMocks
    private BusinessProcessServiceImpl service;

    @Test
    void shouldCreateBusinessProcessWithExistingDomainAndOperator() {
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(new DataDomainPO());
        when(businessProcessDao.queryCount(any())).thenReturn(0L);
        when(businessProcessDao.insert(any())).thenReturn(1);

        String id = service.create(dto(), "alice");

        ArgumentCaptor<BusinessProcessPO> captor = ArgumentCaptor.forClass(BusinessProcessPO.class);
        verify(businessProcessDao).insert(captor.capture());
        assertEquals(32, id.length());
        assertEquals("alice", captor.getValue().getCreateBy());
        assertEquals("alice", captor.getValue().getUpdateBy());
        assertEquals(DOMAIN_ID, captor.getValue().getDataDomainId());
    }

    @Test
    void shouldRejectBusinessProcessWhenDomainDoesNotExist() {
        when(dataDomainDao.queryById(DOMAIN_ID)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto()));

        assertEquals("所选数据域不存在或不属于当前项目", exception.getMessage());
        verify(businessProcessDao, never()).insert(any());
    }

    @Test
    void shouldPreventDeletingReferencedBusinessProcess() {
        BusinessProcessPO process = new BusinessProcessPO();
        process.setFlag(0);
        when(businessProcessDao.queryById(PROCESS_ID)).thenReturn(process);
        when(dimensionTableDao.queryCount(any())).thenReturn(3L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(PROCESS_ID));

        assertTrue(exception.getMessage().contains("已被维度模型引用（3条）"));
        verify(businessProcessDao, never()).deleteById(PROCESS_ID);
    }

    @Test
    void shouldReturnEmptyTreeWhenBusinessTypeHasNoRelatedDomains() {
        when(businessTypeRelatedDataDomainDao.queryList(any())).thenReturn(Collections.emptyList());

        assertTrue(service.treeList("business-type-id").isEmpty());
        verify(businessProcessDao, never()).queryList(any());
    }

    private BusinessProcessDTO dto() {
        BusinessProcessDTO dto = new BusinessProcessDTO();
        dto.setEnShortName("admission");
        dto.setEnName("Admission");
        dto.setCnName("入院");
        dto.setDataDomainId(DOMAIN_ID);
        dto.setDirector("owner");
        return dto;
    }
}
