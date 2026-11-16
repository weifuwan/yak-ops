package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.measurementunit.MeasurementUnitDTO;
import io.yak.ops.modeling.api.vo.measurementunit.MeasurementUnitVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for MeasurementUnit. */
public interface MeasurementUnitService extends ModelingCrudService<MeasurementUnitDTO, MeasurementUnitVO> {
}
