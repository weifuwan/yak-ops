package io.yak.ops.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTreeNodeVO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.subjectdomain.SubjectDomainVO;
import io.yak.ops.modeling.domain.enums.DimensionTreeGroupingEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class DimensionTreeBuilderTest {

    @Mock private DimensionTableDao dimensionTableDao;
    @Mock private DwLayerService dwLayerService;
    @Mock private BusinessTypeService businessTypeService;
    @Mock private DataDomainService dataDomainService;
    @Mock private BusinessProcessService businessProcessService;
    @Mock private DataMartService dataMartService;
    @Mock private SubjectDomainService subjectDomainService;

    @InjectMocks
    private DimensionTreeBuilder builder;

    @Test
    void shouldRequireWarehouseLayerType() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildTree(new DimensionTableDTO()));
    }

    @Test
    void shouldBuildSourceLayerTreeFromPersistedDimensionTables() {
        when(dwLayerService.all(any())).thenReturn(Collections.singletonList(layer("source-layer")));
        DimensionTablePO sourceTable = table("table-1", "business-child", null, null);
        sourceTable.setTableType(DimensionTableTypeEnum.SOURCE);
        when(dimensionTableDao.queryList(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(sourceTable));
        when(businessTypeService.all()).thenReturn(Arrays.asList(
                businessType("business-root", null),
                businessType("business-child", "business-root")));

        DimensionTableDTO dto = new DimensionTableDTO();
        dto.setDwhType(DwTypeEnum.SOURCE_LAYER);
        dto.setSelectOption(DimensionTreeGroupingEnum.BUSINESS_TYPE);

        DimensionTreeNodeVO root = builder.buildTree(dto).get(0);

        assertEquals("DimensionTable_ROOT", root.getKey());
        assertEquals(1, root.getTotal());
        assertEquals(1, root.getChildren().size());
        assertEquals("business-root", root.getChildren().get(0).getKey());
        assertEquals("business-child", root.getChildren().get(0).getChildren().get(0).getKey());
        DimensionTreeNodeVO sourceLeaf = root.getChildren().get(0).getChildren().get(0).getChildren().get(0);
        assertEquals("table-1-DIM", sourceLeaf.getKey());
        assertEquals("SOURCE", sourceLeaf.getType());
    }

    @Test
    void shouldKeepSelectedBusinessTypeBranchAndItsDescendantTables() {
        when(dwLayerService.all(any())).thenReturn(Collections.singletonList(layer("source-layer")));
        when(dimensionTableDao.queryList(any(QueryWrapper.class))).thenReturn(Arrays.asList(
                table("table-child", "business-child", null, null),
                table("table-other", "business-other", null, null)));
        when(businessTypeService.all()).thenReturn(Arrays.asList(
                businessType("business-root", null),
                businessType("business-child", "business-root"),
                businessType("business-other", null)));

        DimensionTableDTO dto = new DimensionTableDTO();
        dto.setDwhType(DwTypeEnum.SOURCE_LAYER);
        dto.setSelectOption(DimensionTreeGroupingEnum.BUSINESS_TYPE);
        dto.setBusTypeId("business-root");

        DimensionTreeNodeVO root = builder.buildTree(dto).get(0);

        assertEquals(1, root.getChildren().size());
        assertEquals("business-root", root.getChildren().get(0).getKey());
        assertEquals(1, root.getTotal());
        assertEquals("table-child-DIM",
                root.getChildren().get(0).getChildren().get(0).getChildren().get(0).getKey());
    }

    @Test
    void shouldKeepTheAncestorPathWhenFilteringApplicationLayerBySubjectDomain() {
        when(dwLayerService.all(any())).thenReturn(Collections.singletonList(layer("application-layer")));
        DimensionTablePO applicationTable = table("table-1", "business-1", "mart-1", "subject-1");
        applicationTable.setTableType(DimensionTableTypeEnum.APPLICATION);
        when(dimensionTableDao.queryList(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(applicationTable));
        when(businessTypeService.all()).thenReturn(Collections.singletonList(businessType("business-1", null)));
        when(dataMartService.all()).thenReturn(Collections.singletonList(mart("mart-1", "business-1")));
        when(subjectDomainService.all()).thenReturn(Collections.singletonList(subject("subject-1", "mart-1")));

        DimensionTableDTO dto = new DimensionTableDTO();
        dto.setDwhType(DwTypeEnum.APPLICATION_LAYER);
        dto.setSelectOption(DimensionTreeGroupingEnum.SUBJECT_DOMAIN);
        dto.setSubjectId("subject-1");

        DimensionTreeNodeVO root = builder.buildTree(dto).get(0);

        assertEquals(1, root.getTotal());
        DimensionTreeNodeVO businessType = root.getChildren().get(0);
        assertEquals("business-1", businessType.getKey());
        DimensionTreeNodeVO dataMart = businessType.getChildren().get(0);
        assertEquals("mart-1", dataMart.getKey());
        DimensionTreeNodeVO subjectDomain = dataMart.getChildren().get(0);
        assertEquals("subject-1", subjectDomain.getKey());
        DimensionTreeNodeVO applicationLeaf = subjectDomain.getChildren().get(0);
        assertEquals("table-1-DIM", applicationLeaf.getKey());
        assertEquals("APPLICATION", applicationLeaf.getType());
    }

    @Test
    void shouldRejectCommonLayerGroupingForApplicationLayer() {
        DimensionTableDTO dto = new DimensionTableDTO();
        dto.setDwhType(DwTypeEnum.APPLICATION_LAYER);
        dto.setSelectOption(DimensionTreeGroupingEnum.BUSINESS_TYPE);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.buildTree(dto));

        assertEquals("应用层目录仅支持按数据集市或主题域分组", exception.getMessage());
    }

    private DwLayerVO layer(String id) {
        DwLayerVO layer = new DwLayerVO();
        layer.setId(id);
        return layer;
    }

    private BusinessTypeVO businessType(String id, String parentId) {
        BusinessTypeVO businessType = new BusinessTypeVO();
        businessType.setId(id);
        businessType.setBusinessName(id);
        businessType.setParentId(parentId);
        return businessType;
    }

    private DataMartVO mart(String id, String businessTypeId) {
        DataMartVO mart = new DataMartVO();
        mart.setId(id);
        mart.setMartName(id);
        mart.setBusinessTypeId(businessTypeId);
        return mart;
    }

    private SubjectDomainVO subject(String id, String dataMartId) {
        SubjectDomainVO subject = new SubjectDomainVO();
        subject.setId(id);
        subject.setDomainName(id);
        subject.setDataMartId(dataMartId);
        return subject;
    }

    private DimensionTablePO table(String id, String businessTypeId, String martId, String subjectId) {
        DimensionTablePO table = new DimensionTablePO();
        table.setId(id);
        table.setCnName(id);
        table.setTableName(id);
        table.setDwhId("layer-id");
        table.setBusTypeId(businessTypeId);
        table.setMartId(martId);
        table.setSubjectId(subjectId);
        table.setTableType(DimensionTableTypeEnum.DIM);
        return table;
    }
}
