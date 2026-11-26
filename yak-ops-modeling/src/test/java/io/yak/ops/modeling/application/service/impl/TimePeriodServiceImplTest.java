package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.TimePeriodCategoryPO;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodCategoryDTO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodCategoryVO;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimePeriodServiceImplTest {

    @Mock
    private ModelingAssetDao modelingAssetDao;

    @InjectMocks
    private TimePeriodServiceImpl service;

    @Test
    void shouldReturnOnlyOneCategoryWhenLegacyRowsContainDuplicates() {
        TimePeriodCategoryPO root = category("root", "TIME_PERIOD", null, Boolean.FALSE);
        TimePeriodCategoryPO duplicateRoot = category("root-copy", "TIME_PERIOD", null, Boolean.FALSE);
        TimePeriodCategoryPO day = category("day", "DAY", "root", Boolean.TRUE);
        TimePeriodCategoryPO duplicateDay = category("day-copy", "DAY", "root-copy", Boolean.TRUE);
        when(modelingAssetDao.queryCount(eq("time-period-category"), any())).thenReturn(4L);
        when(modelingAssetDao.queryList(eq("time-period-category"), any()))
                .thenReturn(Arrays.asList(root, duplicateRoot, day, duplicateDay));

        List<TimePeriodCategoryVO> result = service.listCategories(new TimePeriodCategoryDTO());

        assertEquals(2, result.size());
        assertEquals("TIME_PERIOD", result.get(0).getEnName());
        assertEquals("DAY", result.get(1).getEnName());
    }

    @Test
    void shouldRejectDuplicateCategoryCodeAcrossTheProject() {
        TimePeriodCategoryDTO dto = new TimePeriodCategoryDTO();
        dto.setCnName("自然日");
        dto.setEnName("day");
        dto.setParentId("root");
        when(modelingAssetDao.queryCount(eq("time-period-category"), any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createCategory(dto, "admin"));

        assertEquals("时间周期粒度英文名称已存在：day", exception.getMessage());
        verify(modelingAssetDao, never()).insert(eq("time-period-category"), any(TimePeriodCategoryPO.class));
    }

    private TimePeriodCategoryPO category(String id, String enName, String parentId, Boolean leaf) {
        TimePeriodCategoryPO category = new TimePeriodCategoryPO();
        category.setId(id);
        category.setEnName(enName);
        category.setCnName(enName);
        category.setParentId(parentId);
        category.setLeaf(leaf);
        return category;
    }
}
