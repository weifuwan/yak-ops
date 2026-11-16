package io.yak.ops.modeling.api.vo.timeperiod;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for TimePeriod. */
@Data
@EqualsAndHashCode(callSuper = true)
public class TimePeriodVO extends ModelingBaseVO {

    private String categoryId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String remark;
    private Boolean submit;
}
