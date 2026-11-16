package io.yak.ops.modeling.api.dto.timeperiod;

import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for TimePeriodCategory. */
@Data
@EqualsAndHashCode(callSuper = true)
public class TimePeriodCategoryDTO extends ModelingQueryDTO {

    private String enName;
    private String cnName;
    private String parentId;
    private Boolean leaf;
}
