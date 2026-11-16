package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodDTO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodVO;
import java.util.List;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodCategoryDTO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodCategoryVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for TimePeriod. */
public interface TimePeriodService extends VersionedModelingAssetService<TimePeriodDTO, TimePeriodVO> {

    List<TimePeriodCategoryVO> listCategories();
    String createCategory(TimePeriodCategoryDTO dto, String operator);
    TimePeriodCategoryVO updateCategory(String id, TimePeriodCategoryDTO dto, String operator);
    String deleteCategory(String id);
}
