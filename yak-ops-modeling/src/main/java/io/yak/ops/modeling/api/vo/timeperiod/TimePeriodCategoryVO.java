package io.yak.ops.modeling.api.vo.timeperiod;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for TimePeriodCategory. */
@Data
@EqualsAndHashCode(callSuper = true)
public class TimePeriodCategoryVO extends ModelingBaseVO {

    private String enName;
    private String cnName;
    private String parentId;
    private Boolean leaf;
}
