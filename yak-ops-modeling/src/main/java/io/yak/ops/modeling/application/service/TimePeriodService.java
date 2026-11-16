package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodCategoryDTO;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodDTO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodCategoryVO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodVO;
import java.util.List;

/** Typed service contract for time periods and their categories. */
public interface TimePeriodService extends VersionedModelingAssetService<TimePeriodDTO, TimePeriodVO> {

    IPage<TimePeriodCategoryVO> pageCategories(TimePeriodCategoryDTO query);

    List<TimePeriodCategoryVO> listCategories(TimePeriodCategoryDTO query);

    TimePeriodCategoryVO getCategoryById(String id);

    String createCategory(TimePeriodCategoryDTO dto, String operator);

    TimePeriodCategoryVO updateCategory(String id, TimePeriodCategoryDTO dto, String operator);

    String deleteCategory(String id);
}
