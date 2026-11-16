package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.derivedindicator.DerivedIndicatorDTO;
import io.yak.ops.modeling.api.vo.derivedindicator.DerivedIndicatorVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for DerivedIndicator. */
public interface DerivedIndicatorService extends VersionedModelingAssetService<DerivedIndicatorDTO, DerivedIndicatorVO> {
}
