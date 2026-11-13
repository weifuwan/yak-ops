package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.modeling.api.dto.subjectdomain.SubjectDomainDTO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.application.service.DataMartService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubjectDomainServiceImplTest {

    private static final String MART_ID = "mart-id";
    private static final String SUBJECT_ID = "subject-id";

    @Mock
    private DataMartService dataMartService;

    @Mock
    private SubjectDomainDao subjectDomainDao;

    @Mock
    private DimensionTableDao dimensionTableDao;

    @InjectMocks
    private SubjectDomainServiceImpl service;

    @Test
    void shouldCreateSubjectDomainWithValidMartAndOperator() {
        DataMartVO mart = new DataMartVO();
        mart.setId(MART_ID);
        mart.setMartName("科研集市");
        when(dataMartService.selectById(MART_ID)).thenReturn(mart);
        when(subjectDomainDao.queryCount(any())).thenReturn(0L);
        when(subjectDomainDao.insert(any())).thenReturn(1);

        String id = service.create(dto(), "alice");

        assertEquals(32, id.length());
        org.mockito.ArgumentCaptor<SubjectDomainPO> captor =
                org.mockito.ArgumentCaptor.forClass(SubjectDomainPO.class);
        verify(subjectDomainDao).insert(captor.capture());
        assertEquals("alice", captor.getValue().getCreateBy());
        assertEquals(MART_ID, captor.getValue().getDataMartId());
    }

    @Test
    void shouldRejectParentFromAnotherDataMart() {
        DataMartVO mart = new DataMartVO();
        mart.setId(MART_ID);
        when(dataMartService.selectById(MART_ID)).thenReturn(mart);
        SubjectDomainPO parent = new SubjectDomainPO();
        parent.setDataMartId("another-mart");
        when(subjectDomainDao.queryById("parent-id")).thenReturn(parent);

        SubjectDomainDTO dto = dto();
        dto.setParentId("parent-id");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(dto));

        assertEquals("父主题域与当前主题域必须属于同一个数据集市", exception.getMessage());
        verify(subjectDomainDao, never()).insert(any());
    }

    @Test
    void shouldPreventDeletingSubjectDomainReferencedByDimensionModel() {
        when(subjectDomainDao.queryById(SUBJECT_ID)).thenReturn(new SubjectDomainPO());
        when(subjectDomainDao.queryCount(any())).thenReturn(0L);
        when(dimensionTableDao.queryCount(any())).thenReturn(2L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(SUBJECT_ID));

        assertEquals("该主题域已被维度模型引用（2条），不能删除", exception.getMessage());
        verify(subjectDomainDao, never()).deleteById(SUBJECT_ID);
    }

    private SubjectDomainDTO dto() {
        SubjectDomainDTO dto = new SubjectDomainDTO();
        dto.setEnShortName("research");
        dto.setDomainName("科研主题域");
        dto.setDirector("owner");
        dto.setDataMartId(MART_ID);
        return dto;
    }
}
