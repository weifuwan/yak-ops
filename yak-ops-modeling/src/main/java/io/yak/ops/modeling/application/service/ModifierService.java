package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.modifier.ModifierDTO;
import io.yak.ops.modeling.api.vo.modifier.ModifierVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for Modifier. */
public interface ModifierService extends VersionedModelingAssetService<ModifierDTO, ModifierVO> {
}
