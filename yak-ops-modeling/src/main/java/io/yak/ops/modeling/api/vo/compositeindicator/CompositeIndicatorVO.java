package io.yak.ops.modeling.api.vo.compositeindicator;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

/** Typed response model for CompositeIndicator. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompositeIndicatorVO extends ModelingBaseVO {

    private DwTypeEnum dwType;
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
    private String computeMode;
    private String computeExpr;
    private String deriveId;
    private String compareType;
    private Boolean submit;
}
