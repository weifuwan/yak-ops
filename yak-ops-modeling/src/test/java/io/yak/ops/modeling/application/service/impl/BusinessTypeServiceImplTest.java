package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessTypeServiceImplTest {

    private static final String BUSINESS_TYPE_ID = "business-type-id";

    @Mock
    private BusinessTypeDao businessTypeDao;

    @Mock
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Mock
    private DataMartDao dataMartDao;

    @InjectMocks
    private BusinessTypeServiceImpl service;

    @Test
    void shouldRejectDeleteWhenChildBusinessTypesExist() {
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(new BusinessTypePO());
        when(businessTypeDao.queryCount(any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(BUSINESS_TYPE_ID));

        assertEquals("该业务分类下存在子业务分类，不能删除", exception.getMessage());
        verify(businessTypeDao, never()).deleteById(BUSINESS_TYPE_ID);
    }

    @Test
    void shouldRejectDeleteWhenDataDomainsAreLinked() {
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(new BusinessTypePO());
        when(businessTypeDao.queryCount(any())).thenReturn(0L);
        when(businessTypeRelatedDataDomainDao.queryCount(any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(BUSINESS_TYPE_ID));

        assertEquals("该业务分类已关联数据域，不能删除", exception.getMessage());
        verify(businessTypeDao, never()).deleteById(BUSINESS_TYPE_ID);
    }

    @Test
    void shouldRejectDeleteWhenDataMartsAreLinked() {
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(new BusinessTypePO());
        when(businessTypeDao.queryCount(any())).thenReturn(0L);
        when(businessTypeRelatedDataDomainDao.queryCount(any())).thenReturn(0L);
        when(dataMartDao.queryCount(any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(BUSINESS_TYPE_ID));

        assertEquals("该业务分类下存在数据集市，不能删除", exception.getMessage());
        verify(businessTypeDao, never()).deleteById(BUSINESS_TYPE_ID);
    }

    @Test
    void shouldDeleteBusinessTypeWhenNoDependenciesExist() {
        when(businessTypeDao.queryById(BUSINESS_TYPE_ID)).thenReturn(new BusinessTypePO());
        when(businessTypeDao.queryCount(any())).thenReturn(0L);
        when(businessTypeRelatedDataDomainDao.queryCount(any())).thenReturn(0L);
        when(dataMartDao.queryCount(any())).thenReturn(0L);

        assertEquals(BUSINESS_TYPE_ID, service.delete(BUSINESS_TYPE_ID));
        verify(businessTypeDao).deleteById(BUSINESS_TYPE_ID);
    }
}
