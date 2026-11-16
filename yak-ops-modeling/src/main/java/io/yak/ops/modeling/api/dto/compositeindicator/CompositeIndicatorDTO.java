package io.yak.ops.modeling.api.dto.compositeindicator;

import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for CompositeIndicator. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompositeIndicatorDTO extends ModelingQueryDTO {

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
    private String computeMode;
    private String computeExpr;
    private String deriveId;
    private String compareType;
    private Boolean submit;
}
