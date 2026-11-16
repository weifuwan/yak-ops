package io.yak.ops.modeling.api.vo.measurementunit;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for MeasurementUnit. */
@Data
@EqualsAndHashCode(callSuper = true)
public class MeasurementUnitVO extends ModelingBaseVO {

    private String enShortName;
    private String enName;
    private String cnName;
    private String parentId;
}
