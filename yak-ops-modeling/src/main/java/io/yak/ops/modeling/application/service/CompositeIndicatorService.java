package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.compositeindicator.CompositeIndicatorDTO;
import io.yak.ops.modeling.api.vo.compositeindicator.CompositeIndicatorVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for CompositeIndicator. */
public interface CompositeIndicatorService extends VersionedModelingAssetService<CompositeIndicatorDTO, CompositeIndicatorVO> {
}
