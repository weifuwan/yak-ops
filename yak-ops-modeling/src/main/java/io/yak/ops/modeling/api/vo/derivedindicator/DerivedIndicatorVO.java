package io.yak.ops.modeling.api.vo.derivedindicator;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for DerivedIndicator. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DerivedIndicatorVO extends ModelingBaseVO {

    private String timePeriodId;
    private String modifierId;
    private String atomicId;
    private String dwhId;
    private String martId;
    private String subjectId;
    private String busTypeId;
    private String domainId;
    private String busProcessId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String remark;
    private Boolean submit;
}
