package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.atomindicator.AtomIndicatorDTO;
import io.yak.ops.modeling.api.vo.atomindicator.AtomIndicatorVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for AtomIndicator. */
public interface AtomIndicatorService extends VersionedModelingAssetService<AtomIndicatorDTO, AtomIndicatorVO> {
}
