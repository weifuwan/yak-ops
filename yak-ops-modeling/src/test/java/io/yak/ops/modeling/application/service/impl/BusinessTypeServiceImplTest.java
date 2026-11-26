package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.modeling.api.dto.businesstype.BusinessTypeDTO;
import org.mockito.ArgumentCaptor;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
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

    @Mock
    private ModifierDao modifierDao;

    @InjectMocks
    private BusinessTypeServiceImpl service;

    @Test
    void shouldNormalizeBusinessTypeShortNameToLowerCase() {
        when(businessTypeDao.queryCount(any())).thenReturn(0L);
        when(businessTypeDao.insert(any())).thenReturn(1);

        BusinessTypeDTO dto = new BusinessTypeDTO();
        dto.setBusinessName("零售业务");
        dto.setEnShortName("Retail_Sales");
        dto.setDirector("owner");

        service.create(dto);

        ArgumentCaptor<BusinessTypePO> captor = ArgumentCaptor.forClass(BusinessTypePO.class);
        verify(businessTypeDao).insert(captor.capture());
        assertEquals("retail_sales", captor.getValue().getEnShortName());
    }

    @Test
    void shouldRejectDuplicateBusinessTypeShortName() {
        when(businessTypeDao.queryCount(any())).thenReturn(1L);

        BusinessTypeDTO dto = new BusinessTypeDTO();
        dto.setBusinessName("零售业务");
        dto.setEnShortName("retail_sales");
        dto.setDirector("owner");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto));

        assertEquals("业务分类英文缩写已存在：retail_sales", exception.getMessage());
        verify(businessTypeDao, never()).insert(any());
    }

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
