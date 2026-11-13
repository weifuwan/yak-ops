package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.modeling.application.service.BusinessTypeService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataMartServiceImplTest {

    private static final String DATA_MART_ID = "data-mart-id";

    @Mock
    private BusinessTypeService businessTypeService;

    @Mock
    private DataMartDao dataMartDao;

    @Mock
    private SubjectDomainDao subjectDomainDao;

    @Mock
    private DimensionTableDao dimensionTableDao;

    @InjectMocks
    private DataMartServiceImpl service;

    @Test
    void shouldRejectDeleteWhenChildDataMartsExist() {
        when(dataMartDao.queryById(DATA_MART_ID)).thenReturn(new DataMartPO());
        when(dataMartDao.queryCount(any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(DATA_MART_ID));

        assertEquals("该数据集市下存在下级数据集市（1条），不能删除", exception.getMessage());
        verify(dataMartDao, never()).deleteById(DATA_MART_ID);
    }

    @Test
    void shouldRejectDeleteWhenSubjectDomainsAreLinked() {
        when(dataMartDao.queryById(DATA_MART_ID)).thenReturn(new DataMartPO());
        when(dataMartDao.queryCount(any())).thenReturn(0L);
        when(subjectDomainDao.queryCount(any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(DATA_MART_ID));

        assertEquals("该数据集市已被引用，不能删除（主题域 1 条、维度模型 0 条）", exception.getMessage());
        verify(dataMartDao, never()).deleteById(DATA_MART_ID);
    }

    @Test
    void shouldRejectDeleteWhenDimensionTablesAreLinked() {
        when(dataMartDao.queryById(DATA_MART_ID)).thenReturn(new DataMartPO());
        when(dataMartDao.queryCount(any())).thenReturn(0L);
        when(subjectDomainDao.queryCount(any())).thenReturn(0L);
        when(dimensionTableDao.queryCount(any())).thenReturn(2L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(DATA_MART_ID));

        assertEquals("该数据集市已被引用，不能删除（主题域 0 条、维度模型 2 条）", exception.getMessage());
        verify(dataMartDao, never()).deleteById(DATA_MART_ID);
    }

    @Test
    void shouldDeleteDataMartWhenNoDependenciesExist() {
        when(dataMartDao.queryById(DATA_MART_ID)).thenReturn(new DataMartPO());
        when(dataMartDao.queryCount(any())).thenReturn(0L);
        when(subjectDomainDao.queryCount(any())).thenReturn(0L);
        when(dimensionTableDao.queryCount(any())).thenReturn(0L);
        when(dataMartDao.deleteById(DATA_MART_ID)).thenReturn(true);

        assertEquals(DATA_MART_ID, service.delete(DATA_MART_ID));
        verify(dataMartDao).deleteById(DATA_MART_ID);
    }
}
