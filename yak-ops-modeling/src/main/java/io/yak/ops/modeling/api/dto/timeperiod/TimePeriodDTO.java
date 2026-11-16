package io.yak.ops.modeling.api.dto.timeperiod;

import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for TimePeriod. */
@Data
@EqualsAndHashCode(callSuper = true)
public class TimePeriodDTO extends ModelingQueryDTO {

    private String categoryId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String remark;
    private Boolean submit;
}
