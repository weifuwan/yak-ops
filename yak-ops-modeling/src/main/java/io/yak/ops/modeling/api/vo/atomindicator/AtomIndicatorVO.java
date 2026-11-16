package io.yak.ops.modeling.api.vo.atomindicator;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

/** Typed response model for AtomIndicator. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AtomIndicatorVO extends ModelingBaseVO {

    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String remark;
    private String businessCaliber;
    private String busTypeId;
    private String domainId;
    private String busProcessId;
    private DwTypeEnum dwType;
    private String calculateFunction;
    private Integer decimalNumber;
    private String unitId;
    private Boolean removeDuplicates;
    private Boolean submit;
}
